package ru.serbis.okto.node.hardware

import akka.actor.{Actor, ActorRef, Props, Timers}
import akka.util.ByteString
import ru.serbis.okto.node.common.ReachTypes.ReachByteString
import ru.serbis.okto.node.hardware.NativeApi._
import ru.serbis.okto.node.log.Logger.LogEntryQualifier
import ru.serbis.okto.node.log.StreamLogger

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

/** This actor is designed to implement the interaction layer with the system node daemon. This daemon is used to perform
  * system actions requiring privileged user rights. From the interaction side, the daemon creates a domain socket through
  * which the daemon can be controlled via the OTPP protocol. The protocol operates in the request-response mode, so this
  * actor implements the abstraction of the request-response interaction with this daemon. To implement the logic of working
  * with domain sockets, native calls from the NativeApi library are used.
  *
  */
object SystemDaemon {

  /** @param socket path to the unix domain socket control file (created by NSD)
    * @param maxReq maximum processing request at the time. If an attempt is made to send a request, this threshold is
    *               exceeded, then BridgeOverload is sent in response to the requester
    * @param cleanerTime cleaner timer interval. For more details see appropriate comment below
    * @param emulatorMode Used to enable the hwLib_e version of the native library. Technically, the functions for working with
    *                     sockets in it are identical to those in the original library. This mode is necessary due to the fact
    *                     that the original library cannot be compiled for x86 architecture.
    */
  def props(socket: String, maxReq: Int, cleanerTime: FiniteDuration = 1 second, emulatorMode: Boolean = false) =
    Props(new SystemDaemon(socket, maxReq, cleanerTime, emulatorMode))

  object Commands {

    /** Makes a request to the NST. The latter acquires some result, which is sent to the sender in the message DaemonResponse.
      * If the response from the NSD does not occur within a certain time period, the sender returns ResponseTimeout. If the NSD
      * returned an error, the sender returns DaemonError. If the bridge request pool is full the sender returns DaemonOverload.
      *
      * @param req request to the NSD
      * @param meta label
      * @param timeout request timeout
      */
    case class DaemonRequest(req: String, meta: Any, timeout: Long = 1000)
  }

  object Responses {

    /** Correct response for NSD command
      *
      * @param resp data from the NSD
      * @param meta label
      */
    case class DaemonResponse(resp: String, meta: Any)

    /** NSD does not respond with specified timeout
      *
      * @param meta label
      */
    case class ResponseTimeout(meta: Any)

    /** NSD respond with daemon error
      *
      * @param reason error reason
      * @param meta label
      */
    case class DaemonError(reason: String, meta: Any)

    /** No free space in the requests buffer row new request
      *
      * @param meta label
      */
    case class DaemonOverload(meta: Any)
  }

  object Internals {

    /** A message with a data packet sent to the actor from the daemon socket
      *
      * @param data byte array
      */
    case class DaemonData(data: ByteString)

    /** Message purification of requests. This message is sent by the timer through the specified time intervals
      * defined by the cleanerTime value in the actor constructor. The task of the handler is to remove requests from
      * the pool, those response deadline from the NSD was passed. In addition to removing the request from
      * the pool, the RequestTimeout response is sent to its initiator */
    case object CleanerTick

    /** Processed response descriptor
      *
      * @param meta request label
      * @param sender originator
      * @param deadline request deadline (time of request starting + request timeout)
      * @param meta metadata witch will be in transcation complete message
      */
    case class RequestDescriptor(meta: Any, sender: ActorRef, deadline: Long)
  }
}
class SystemDaemon(socket: String, maxReq: Int, cleanerTime: FiniteDuration, emulatorMode: Boolean) extends Actor with StreamLogger with Timers {
  import SystemDaemon.Commands._
  import SystemDaemon.Internals._
  import SystemDaemon.Responses._

  setLogSourceName(s"SystemDaemon*${self.path.name}")
  setLogKeys(Seq("SystemDaemon"))
  implicit val logQualifier = LogEntryQualifier("static")

  /** The file descriptor of the client socket leading to the daemon */
  val sockfd = unixDomainConnect(ByteString(socket).toArray)

  /** Current requests table */
  var reqTable = mutable.HashMap.empty[Int, RequestDescriptor]

  /** The thread of data reading from the socket. Its task is to constantly poll the socket and buffering the
    * received data. When the stream receives the packet separator (\r), the buffer assembles the byte array and
    * is sent to the system daemon actor in the DaemonData message */
  val reader = if (sockfd == -1) {   //NOT TESTABLE
    logger.fatal(s"Could not open NSD socket '$socket'")

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
            val c = unixDomainReadChar(sockfd)
            if (c != -1) {
              if (c != '\r') {
                buff += c.toByte
              } else {
                buff += c.toByte
                self ! DaemonData(ByteString((1 to buff.size).foldLeft(Vector.empty[Byte])((a, _) => a :+ buff.dequeue()).toArray))
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
    logger.info("SystemDaemon actor is initialized")
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

      if (sockfd != -1)
        unixDomainClose(sockfd)

      logger.debug("System daemon connector was stopped")
    }
  }

  override def receive = {

    /** See the message description */
    case DaemonRequest(req, meta, timeout) =>
      implicit val logQualifier = LogEntryQualifier("DaemonRequest")
      if (reqTable.size < maxReq) {
        logger.debug(s"Started request '$req' with meta '$meta' from the requestor '${sender.path.name}'")
        val msgNum = if (reqTable.isEmpty) 1 else reqTable.maxBy(_._1)._1 + 1
        reqTable += msgNum -> RequestDescriptor(meta, sender, System.currentTimeMillis() + timeout)
        unixDomainWrite(sockfd, ByteString(s"c\t$msgNum\t$req\r").toArray)
      } else {
        logger.warning(s"Unable to request '$req' with meta '$meta' from the requestor '${sender.path.name}'. Daemon is overloaded")
        sender ! DaemonOverload
      }

    /** See the message description */
    case DaemonData(data) =>
      implicit val logQualifier = LogEntryQualifier("DaemonData")
      val spl = data.utf8String.split("\t")
      if (spl.length != 3) {
        logger.error(s"Data from the daemon is corrupted. Corrupted data '${data.toHexString}'")
      } else {
        val msgid = try { spl(1).toInt } catch { case _: Exception => -1}
        spl(0) match {
          case "r" =>
            finishRequest(msgid, data) { rd =>
              rd.sender ! DaemonResponse(spl(2).dropRight(1), rd.meta)
              reqTable -= msgid
              logger.debug(s"Returned response '${spl(2).dropRight(1)}' with meta '${rd.meta}' to the requestor '${rd.sender.path.name}'")
            }

          case "e" =>
            finishRequest(msgid, data) { rd =>
              rd.sender ! DaemonError(spl(2).dropRight(1), rd.meta)
              reqTable -= msgid
              logger.warning(s"Returned daemon error '${spl(2).dropRight(1)}' with meta '${rd.meta}' to the requestor '${rd.sender.path.name}'")
            }

          case e =>
            logger.error(s"The message received from the damon has an incorrect qualifier type '$e'")
        }
      }

    /** See the message description */
    case CleanerTick =>
      implicit val logQualifier = LogEntryQualifier("CleanerTick")
      reqTable = reqTable.foldLeft(mutable.HashMap.empty[Int, RequestDescriptor])((a, v) => {
        if (System.currentTimeMillis() > v._2.deadline) {
          logger.warning(s"The timeout for waiting for a response from the daemon was reached on request from '${v._2.sender.path.name}' with the meta '${v._2.meta}'")
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
        logger.warning(s"Received response from daemon with not registered msgid '$msgid'")
      }
    } else {
      logger.warning(s"Received response from daemon with corrupted msgid block '${data.toHexString}'")
    }
  }
}