package ru.serbis.okto.node.hardware

import akka.actor.{Actor, ActorRef, Props, Timers}
import akka.util.ByteString
import ru.serbis.okto.node.common.ReachTypes.ReachByteString
import ru.serbis.okto.node.hardware.NativeApi._
import ru.serbis.okto.node.log.Logger.LogEntryQualifier
import ru.serbis.okto.node.log.StreamLogger
import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

/** This actor (hereinafter referred to as the bridge) serves to create a layer of interaction with the expansion board.
  * The expansion board is controlled via a serial port through the WiringPi native library. To interact with the
  * latter, there is a realization of native calls presented in the NativeApi object. To exchange data with the
  * underlying controller, a text protocol consisting of packets is used. To separate the packets, use the \r symbol.
  * The fields inside the package are separated by the symbol \t. The package itself consists of the following fields:
  * [message classifier] [message number] [message body]. The message classifier is used to determine the type of
  * message. At the moment there are classifiers with c - request, r - answer, e - hardware error. The message number
  * is used for the organization of the request-response communication (see below). The body of the message contains a
  * payload. Thus, the package might look like 'c t12\tdata\r'. From the outside of the bridge, the interaction with
  * the expansion board is supported based on the request response principle. A command is sent to the bridge, and in
  * response a response or error is returned. */
object SerialBridge {

  /** @param device path to the serial I / O device. For example '/def/ttyUSB0'
    * @param baud serial port baud rate
    * @param maxReq maximum processing request at the time. If an attempt is made to send a request, this threshold is
    *               exceeded, then BridgeOverload is sent in response to the requestor
    * @param cleanerTime cleaner timer interval. For more details see appropriate comment below
    * @param emulatorMode Defines the serial port emulation mode. Since the full hardware testing of this I / O system
    *                     is associated with very high overhead, a serial port emulation mechanism is used for testing
    *                     purposes. It consists in the fact that if this mode is active, the write and read records
    *                     occur in different file descriptors. When creating an actor, it is passed a link to some path,
    *                     through which two files will be created. One will be a TX line and the other will be RX. So
    *                     when the bridge writes something to the TX file, the test can read this data, and then write
    *                     some response to the RX file. The test stand uses a special implementation of the native
    *                     NativeApi library, which writes data not to the real device but to a regular file. Thus, for
    *                     the bridge, the testing procedure is completely transparent, as if the work is being done with
    *                     a real input-output device.
    */
  def props(device: String, baud: Int, maxReq: Int, cleanerTime: FiniteDuration = 1 second, emulatorMode: Boolean = false) =
    Props(new SerialBridge(device, baud, maxReq, cleanerTime, emulatorMode))

  object Commands {

    /** Makes a request to the expansion card. The latter acquires some result, which is sent to the sender in the
      * message SerialResponse. If the response from the expansion board controller does not occur within a certain time
      * period, the sender returns ResponseTimeout. If the expansion card controller returned an error, the sender
      * returns HardwareError. If the bridge request pool is full the sender returns BridgeOverload.
      *
      * @param req request to the expansion board
      * @param meta label
      * @param timeout request timeout
      */
    case class SerialRequest(req: String, meta: Any, timeout: Long = 1000)
  }

  object Responses {

    /** Correct response for SerialRequest command
      *
      * @param resp data from the expansion board
      * @param meta label
      */
    case class SerialResponse(resp: String, meta: Any)

    /** Expansion board does not respond with specified timeout
      *
      * @param meta label
      */
    case class ResponseTimeout(meta: Any)

    /** Expansion board respond with hardware error
      *
      * @param reason error reason
      * @param meta label
      */
    case class HardwareError(reason: String, meta: Any)

    /** No free space in requsts buffer row new request
      *
      * @param meta label
      */
    case class BridgeOverload(meta: Any)
  }

  object Internals {

    /** A message with a data packet sent to the bridge from the serial port reader thread
      *
      * @param data byte array
      */
    case class SerialData(data: ByteString)

    /** Message purification of requests. This message is sent by the timer through the specified time intervals
      * defined by the cleanerTime value in the actor constructor. The task of the handler is to remove requests from
      * the pool, those response deadline from the expansion board was passed. In addition to removing the request from
      * the pool, the RequestTimeout response is sent to its initiator */
    case object CleanerTick

    /** Processed response descriptor
      *
      * @param meta request label
      * @param sender originator
      * @param deadline request deadline (time of request starting + request timeout)
      */
    case class RequestDescriptor(meta: Any, sender: ActorRef, deadline: Long)
  }
}

class SerialBridge(device: String, baud: Int, maxReq: Int, cleanerTime: FiniteDuration, emulatorMode: Boolean) extends Actor with StreamLogger with Timers {
  import SerialBridge.Commands._
  import SerialBridge.Internals._
  import SerialBridge.Responses._

  setLogSourceName(s"SerialBridge*${self.path.name}")
  setLogKeys(Seq("SerialBridge"))
  implicit val logQualifier = LogEntryQualifier("static")

  val wiredPiInit = wiringPiSetupSys()

  /** File descriptors of input-output. In normal mode, they have identical values. In emulation mode, they point to two
    * different files. For a more detailed description of the emulation mode, see the header commentary to the actor */
  val fdTx = serialOpen(ByteString(if (!emulatorMode) device else device + "_tx").toArray, baud)
  val fdRx = if (!emulatorMode) fdTx else serialOpen(ByteString(device + "_rx").toArray, baud)

  /** Current requests table */
  var reqTable = mutable.HashMap.empty[Int, RequestDescriptor]

  /** The stream of data reading from the serial port. Its task is to constantly poll the device and buffering the
    * received data. When the stream receives the packet separator (\ r), the buffer assembles the byte array and
    * is sent to the bridge actor in the SerialData message */
  val reader = if (fdTx == -1 || fdRx == -1 || wiredPiInit == -1) {   //NOT TESTABLE
    if (fdTx == -1 || fdRx == -1)
      logger.fatal(s"Could not open serial device '$device'")
    else
      logger.fatal(s"Could not initialize wiringPi hardware driver")

    Thread.sleep(3000)

    context.system.terminate()
    None
  } else {
    Some(new Runnable {
        var stop = false
        var alive = true
        val buff = mutable.Queue.empty[Byte]

        override def run(): Unit = {
          while(!stop) {
            val c = serialGetchar(fdRx)
            if (c != -1) {
              if (c != '\r') {
                buff += c.toByte
              } else {
                buff += c.toByte
                self ! SerialData(ByteString((1 to buff.size).foldLeft(Vector.empty[Byte])((a, _) => a :+ buff.dequeue()).toArray))
              }
            }
          }
          alive = false
        } //TODO [10] проверить скорость работы данной конструкции и ввести задержку при необходимости
      }
    )
  }
  if (reader.isDefined) {
    new Thread(reader.get).start()
    logger.info("SerialBridge actor is initialized")
  }

  timers.startPeriodicTimer(0, CleanerTick, cleanerTime)

  /** Stops the stream reader. Waiting until it stops, closes the I / O descriptors. */
  override def postStop(): Unit = {
    implicit val logQualifier = LogEntryQualifier("postStop")

    Future {
      if (reader.isDefined) {
        reader.get.stop = true
        while(reader.get.alive) {}
      }

      if (fdRx != -1)
        serialClose(fdRx)
      if (fdTx != fdRx) {
        if (fdTx != -1)
          serialClose(fdTx)
      }

      logger.debug("Serial bridge was stopped")
    }
  }

  override def receive = {

    /** See the message description */
    case SerialRequest(req, meta, timeout) =>
      implicit val logQualifier = LogEntryQualifier("SerialRequest")
      if (reqTable.size < maxReq) {
        logger.debug(s"Started request '$req' with meta '$meta' from the requestor '${sender.path.name}'")
        val msgNum = if (reqTable.isEmpty) 1 else reqTable.maxBy(_._1)._1 + 1
        reqTable += msgNum -> RequestDescriptor(meta, sender, System.currentTimeMillis() + timeout)
        serialPuts(fdTx, ByteString(s"c\t$msgNum\t$req\r").toArray)
      } else {
        logger.warning(s"Unable to request '$req' with meta '$meta' from the requestor '${sender.path.name}'. Bridge is overloaded")
        sender ! BridgeOverload
      }

    /** See the message description */
    case SerialData(data) =>
      implicit val logQualifier = LogEntryQualifier("SerialData")
      val spl = data.utf8String.split("\t")
      if (spl.length != 3) {
        logger.error(s"Data from the device is corrupted. Corrupted data '${data.toHexString}'")
      } else {
        val msgid = try { spl(1).toInt } catch { case _: Exception => -1}
        spl(0) match {
          case "r" =>
            finishRequest(msgid, data) { rd =>
              rd.sender ! SerialResponse(spl(2).dropRight(1), rd.meta)
              reqTable -= msgid
              logger.debug(s"Returned response '${spl(2).dropRight(1)}' with meta '${rd.meta}' to the requestor '${rd.sender.path.name}'")
            }

          case "e" =>
            finishRequest(msgid, data) { rd =>
              rd.sender ! HardwareError(spl(2).dropRight(1), rd.meta)
              reqTable -= msgid
              logger.warning(s"Returned hardware error '${spl(2).dropRight(1)}' with meta '${rd.meta}' to the requestor '${rd.sender.path.name}'")
            }

          case e =>
            logger.error(s"The message received from the device has an incorrect qualifier type '$e'")
        }
      }

    /** See the message description */
    case CleanerTick =>
      implicit val logQualifier = LogEntryQualifier("CleanerTick")
      reqTable = reqTable.foldLeft(mutable.HashMap.empty[Int, RequestDescriptor])((a, v) => {
        if (System.currentTimeMillis() > v._2.deadline) {
          logger.warning(s"The timeout for waiting for a response from the device was reached on request from '${v._2.sender.path.name}' with the meta '${v._2.meta}'")
          v._2.sender ! ResponseTimeout(v._2.meta)
          a
        } else {
          a += v._1 -> v._2
        }
      })

  }

  /** This func is a component of SerialData message handler. See its comment for more details */
  def finishRequest(msgid: Int, data: ByteString)(f: RequestDescriptor => Any): Unit = {
    if (msgid != -1) {
      val rd = reqTable.get(msgid)
      if (rd.isDefined) {
        f(rd.get)
      } else {
        logger.warning(s"Received response from device with not registered msgid '$msgid'")
      }
    } else {
      logger.warning(s"Received response from device with corrupted msgid block '${data.toHexString}'")
    }
  }
}