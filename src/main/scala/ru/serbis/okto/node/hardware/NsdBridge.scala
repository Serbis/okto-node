package ru.serbis.okto.node.hardware

import akka.actor.{Actor, ActorRef, Props, Timers}
import akka.util.ByteString
import ru.serbis.okto.node.events.{Eventer, HardwareEvent}
import ru.serbis.okto.node.hardware.packets.ExbPacket.{apply => _, _}
import ru.serbis.okto.node.log.Logger.LogEntryQualifier
import ru.serbis.okto.node.log.StreamLogger
import ru.serbis.okto.node.proxy.napi.NativeApiProxy
import ru.serbis.okto.node.hardware.packets.NsdPacket
import ru.serbis.okto.node.hardware.packets.NsdPacket.{NsdCmdPacket, NsdErrorPacket, NsdResultPacket}
import ru.serbis.okto.node.proxy.system.ActorSystemProxy
import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.ExecutionContext.Implicits.global

/** Bridge to NSD system daemon */
object NsdBridge {

  /** @param socket path to the unix domain socket control file (created by WSD)
    * @param maxReq maximum processing request at the time. If an attempt is made to send a request, this threshold is
    *               exceeded, then BridgeOverload is sent in response to the requester
    * @param cleanerTime cleaner timer interval. For more details see appropriate comment below
    * @param nap native api functionality proxy
    * @param system actor system proxy for testing
    * @param testMode test mode flag
    */
  def props(socket: String, maxReq: Int,
            cleanerTime: FiniteDuration = 1 second,
            nap: NativeApiProxy,
            system: ActorSystemProxy,
            testMode: Boolean = false) =
    Props(new NsdBridge(socket, maxReq, cleanerTime, nap, system, testMode))

  object Commands {
    case class SendCommand(cmd: String)
  }

  object Responses {

    /** Unable to exec transaction because req table is full */
    case object BridgeOverload

    /** Nsd does not complete transaction with specified timeout */
    case object TransactionTimeout

    /** Some unknown nsd error */
    case class NsdError(code: Int, message: String)

    /** Empty response for any success nsd operation */
    case class NsdResult(result: String)
  }

  object Internals {

    /** In normal condition this message contain a NSD packet received by native layer from bridge read thread */
    case class NsdPayload(data: ByteString)

    /** Message purification of requests. This message is sent by the timer through the specified time intervals
      * defined by the cleanerTime value in the actor constructor. The task of the handler is to remove requests from
      * the pool, those response deadline from the NSD was passed. In addition to removing the request from
      * the pool, the RequestTimeout response is sent to its initiator */
    case object CleanerTick

    /** Processed response descriptor
      *
      * @param sender originator
      * @param type transaction type, define data expected to back
      * @param deadline request deadline (time of request starting + request timeout)
      * @param meta metadata witch will be in transcation complete message
      */
    case class RequestDescriptor(sender: ActorRef, `type`: Int, deadline: Long, meta: Any)

    case class AckEvent(tid: Int, addr: Int, result: Int)

    val TtSendCommand = 0
  }
}

class NsdBridge(socket: String,
               maxReq: Int,
               cleanerTime: FiniteDuration,
               nap: NativeApiProxy,
               system: ActorSystemProxy,
               testMode: Boolean = false) extends Actor with StreamLogger with Timers {
  import NsdBridge.Commands._
  import NsdBridge.Responses._
  import NsdBridge.Internals._

  setLogSourceName(s"NsdBridge*${self.path.name}")
  setLogKeys(Seq("NsdBridge"))
  implicit val logQualifier = LogEntryQualifier("static")

  /** The file descriptor of the client socket leading to the daemon */
  val sockfd = nap.unixDomainConnect(ByteString(socket).toArray)

  /** Current requests table */
  var reqTable = mutable.HashMap.empty[Int, RequestDescriptor]

  /** The thread of data reading from the socket. Its task is to get NSD packages by calling the native
    * unixDomainReadNsdPacket method. Upon receipt of the package, it is packaged in a NsdPayload message and sent
    * to the main actor for processing. */
  val reader = if (sockfd == -1) { //NOT TESTABLE
    logger.fatal(s"Could not open NSD socket '$socket'")
    Thread.sleep(3000)
    context.system.terminate()
    None
  } else {
    Some(new Runnable {
      var stop = false
      var alive = true

      override def run(): Unit = {
        while(!stop) {
          val packet = nap.unixDomainReadNsdPacket(sockfd, 1000)
          if (!stop)
            self ! NsdPayload(ByteString(packet))
        }
        alive = false
      }
    })
  }
  if (reader.isDefined) {
    new Thread(reader.get).start()
    logger.info("NsdBridge actor is initialized")
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

      if (sockfd != -1) //NOT TESTABLE
        nap.unixDomainClose(sockfd)

      logger.debug("Nsd bridge was stopped")
    }
  }

  override def receive = {

    case SendCommand(cmd) =>
      implicit val logQualifier = LogEntryQualifier("SendCommand")

      val tid = if (reqTable.isEmpty) 1 else reqTable.maxBy(_._1)._1 + 1

      logger.debug(s"Start set cmd transaction '$tid' with data '$cmd' from the requestor '${sender.path.name}'")
      reqTable += tid -> RequestDescriptor(sender, TtSendCommand, System.currentTimeMillis() + ( if (testMode) 500 else 15000 ), "-")
      nap.unixDomainWrite(sockfd, NsdCmdPacket(tid, ByteString(cmd)).toArray)


    /** See the message description */
    case NsdPayload(data) =>
      implicit val logQualifier = LogEntryQualifier("NsdPayload")
      val packet = NsdPacket(data)
      val tid = packet.tid

      val rd = reqTable.get(tid)
      if (rd.isDefined) { // If tid exist, this is transaction initiated by node
        reqTable -= tid

        rd.get.`type` match {

          case TtSendCommand =>
            packet match {
              case p: NsdResultPacket =>
                logger.debug(s"Compete cmd transaction '${p.tid}' with success result with value '${p.body.utf8String}'")
                rd.get.sender ! NsdResult(p.body.utf8String)

              case p: NsdErrorPacket =>
                logger.info(s"Compete cmd transaction '${p.tid}' with error, code '${p.code}' message '${p.message}'")
                rd.get.sender ! NsdError(p.code, p.message)
            }
        }
      }


    /** See the message description */
    case CleanerTick =>
      implicit val logQualifier = LogEntryQualifier("CleanerTick")
      reqTable = reqTable.foldLeft(mutable.HashMap.empty[Int, RequestDescriptor])((a, v) => {
        if (System.currentTimeMillis() > v._2.deadline) {
          logger.warning(s"The timeout for waiting for a response from the nsd was reached on request for transaction '${v._1}'")
          v._2.sender ! TransactionTimeout
          a
        } else {
          a += v._1 -> v._2
        }
      })
  }
}
