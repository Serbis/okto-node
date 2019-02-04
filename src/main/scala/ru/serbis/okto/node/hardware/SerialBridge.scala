package ru.serbis.okto.node.hardware

import akka.actor.{Actor, ActorRef, Props, Timers}
import akka.util.ByteString
import ru.serbis.okto.node.hardware.packets.ExbPacket
import ru.serbis.okto.node.hardware.packets.ExbPacket.{ExbCommandPacket, ExbErrorPacket, ExbResponsePacket}
import ru.serbis.okto.node.log.Logger.LogEntryQualifier
import ru.serbis.okto.node.log.StreamLogger
import ru.serbis.okto.node.proxy.napi.NativeApiProxy
import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

/** This actor implements a high-level interaction layer with the exb device through uart interface. It encapsulates all the
  * low-level mechanics of working with exb data streams, reducing them to a transactional principle. Each
  * request to this actor is a multiple-instance translation of the request-response type with a given timeout. For a
  * more detailed understanding of the work, see the description of commands and technical documentation for the exb.
  */
object SerialBridge {

  /** @param device path to the serial I / O device. For example '/def/ttyUSB0'
    * @param baud serial port baud rate
    * @param maxReq maximum processing request at the time. If an attempt is made to send a request, this threshold is
    *               exceeded, then BridgeOverload is sent in response to the requestor
    * @param cleanerTime cleaner timer interval. For more details see appropriate comment below
    * @param nap native api functionality proxy
    */
  def props(device: String, baud: Int, maxReq: Int, cleanerTime: FiniteDuration = 1 second, nap: NativeApiProxy) =
    Props(new SerialBridge(device, baud, maxReq, cleanerTime, nap))

  object Commands {

    /** Send command to the uart exb. May respond with: ExbResponse, ExbError, BridgeOverload, ExbBrokenResponse,
      * TransactionTimeout
      *
      * @param cmd command for exb
      * @param timeout transaction live time
      */
    case class ExbCommand(cmd: String, timeout: Int)
  }

  object Responses {

    /** Correct response for ExbCommand message from exb
      *
      * @param payload what respond exb
      */
    case class ExbResponse(payload: String)

    /** Exb receive command initiated by ExbCommand, but some application error was occurred on the exb firmware level
      *
      * @param code error code
      * @param message error message
      */
    case class ExbError(code: Int, message: String)

    /** Exb respond with broken packet */
    case object ExbBrokenResponse

    /** Unable to exec transaction because req table is full */
    case object BridgeOverload

    /** Driver does not complete transaction with specified timeout */
    case object TransactionTimeout
  }

  object Internals {

    /** In normal condition this message contain a exb packet received by native layer from the uart */
    case class UartPayload(data: ByteString)

    /** Message purification of requests. This message is sent by the timer through the specified time intervals
      * defined by the cleanerTime value in the actor constructor. The task of the handler is to remove requests from
      * the pool, those response deadline from the exd was passed. In addition to removing the request from
      * the pool, the RequestTimeout response is sent to its initiator */
    case object CleanerTick

    /** Processed response descriptor
      *
      * @param sender originator
      * @param deadline request deadline (time of request starting + request timeout)
      */
    case class RequestDescriptor(sender: ActorRef, deadline: Long)
  }
}

class SerialBridge(device: String, baud: Int, maxReq: Int, cleanerTime: FiniteDuration = 1 second, nap: NativeApiProxy) extends Actor with StreamLogger with Timers {
  import SerialBridge.Commands._
  import SerialBridge.Internals._
  import SerialBridge.Responses._

  setLogSourceName(s"SerialBridge*${self.path.name}")
  setLogKeys(Seq("SerialBridge"))
  implicit val logQualifier = LogEntryQualifier("static")

  /** The file descriptor of the uart device */
  val serfd = nap.serialOpen(ByteString(device).toArray, baud)

  /** Current requests table */
  var reqTable = mutable.HashMap.empty[Int, RequestDescriptor]

  /** The thread of data reading from the uart device. Its task is to get exb packages by calling the native
    * serialReadExbPacket method. Upon receipt of the package, it is packaged in a UartPayload message and sent
    * to the main actor for processing. */
  val reader = if (serfd == -1) { //NOT TESTABLE
    logger.fatal(s"Could not open uart device '$device'")

    Thread.sleep(3000)
    context.system.terminate()
    None
  } else {
    Some(new Runnable {
      var stop = false
      var alive = true

      override def run(): Unit = {
        while(!stop) {
          val packet = nap.serialReadExbPacket(serfd, 1000)
          if (packet.size > 0) {
            if (!stop)
              self ! UartPayload(ByteString(packet))
          } else {
            Thread.sleep(50)
          }
        }
        alive = false
      }
    })
  }
  if (reader.isDefined) {
    new Thread(reader.get).start()
    logger.info("SerialBridge actor is initialized")
  }

  timers.startPeriodicTimer(0, CleanerTick, cleanerTime)

  /** Stops the reader thread. Waiting until it stops, closes the I / O descriptors. */
  override def postStop(): Unit = {
    implicit val logQualifier = LogEntryQualifier("postStop")

    Future {
      if (reader.isDefined) {
        reader.get.stop = true
        while(reader.get.alive) {}
      }

      if (serfd != -1) //NOT TESTABLE
        nap.serialClose(serfd)

      logger.debug("SerialBridge was stopped")
    }
  }

  override def receive = {

    /** See the message description */
    case ExbCommand(cmd, timeout) =>
      implicit val logQualifier = LogEntryQualifier("ExbCommand")

      val tid = if (reqTable.isEmpty) 1 else reqTable.maxBy(_._1)._1 + 1
      if (reqTable.size < maxReq) {
        logger.debug(s"Start exb command transaction '$tid' to uart with payload '$cmd' and timeout '$timeout' from the requestor '${sender.path.name}'")
        reqTable += tid -> RequestDescriptor(sender, System.currentTimeMillis() + timeout)
        nap.serialPuts(serfd, ExbCommandPacket(tid, cmd).toArray)
      } else {
        logger.warning(s"Unable to exec command transaction '$tid' to uart with payload '$cmd' and timeout '$timeout' from the requestor '${sender.path.name}'. Uart is overloaded")
        sender ! BridgeOverload
      }

    /** See the message description */
    case UartPayload(data) =>
      implicit val logQualifier = LogEntryQualifier("UartPayload")
      val packet = ExbPacket(data)
      packet match {
        case ep: ExbResponsePacket =>
          finishRequest(ep.tid) { d =>
            logger.debug(s"Compete exb command transaction '${ep.tid}' to uart with result '${ep.response}'")
            d.sender ! ExbResponse(ep.response)
          }
        case ep: ExbErrorPacket =>
          finishRequest(ep.tid) { d =>
            logger.info(s"Compete exb command transaction '${ep.tid}' to uart with error '${ep.code}/${ep.message}'")
            d.sender ! ExbError(ep.code, ep.message)
          }
        case _ => //NOT TESTABLE because tid may be corrupted
          logger.warning(s"Compete unknown exb command transaction to uart as exb broken response")
      }

    /** See the message description */
    case CleanerTick =>
      implicit val logQualifier = LogEntryQualifier("CleanerTick")
      reqTable = reqTable.foldLeft(mutable.HashMap.empty[Int, RequestDescriptor])((a, v) => {
        if (System.currentTimeMillis() > v._2.deadline) {
          logger.warning(s"The timeout for waiting for a response from the exb was reached on request for transaction '${v._1}'")
          v._2.sender ! TransactionTimeout
          a
        } else {
          a += v._1 -> v._2
        }
      })
  }

  /** This func is a component of UartPayload message handler. See its comment for more details */
  def finishRequest(tid: Int)(f: RequestDescriptor => Any): Unit = {
    val rd = reqTable.get(tid)
    if (rd.isDefined) {
      reqTable -= tid
      f(rd.get)
    } else { //NOT TESTABLE
      logger.warning(s"Received response from exb with not registered tid '$tid'")
    }
  }
}