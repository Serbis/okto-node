package ru.serbis.okto.node.hardware

import akka.actor.{Actor, ActorRef, Props, Timers}
import akka.util.ByteString
import ru.serbis.okto.node.events.{Eventer, HardwareEvent}
import ru.serbis.okto.node.hardware.packets.ExbPacket.{apply => _, _}
import ru.serbis.okto.node.hardware.packets.{EventConfirmator, WsdPacket}
import ru.serbis.okto.node.hardware.packets.WsdPacket._
import ru.serbis.okto.node.log.Logger.LogEntryQualifier
import ru.serbis.okto.node.log.StreamLogger
import ru.serbis.okto.node.proxy.napi.NativeApiProxy
import ru.serbis.okto.node.common.ReachTypes.ReachByteString
import ru.serbis.okto.node.hardware.CmdReplicator.Supply.SourceBridge
import ru.serbis.okto.node.proxy.system.ActorSystemProxy

import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.duration._
import scala.concurrent.duration.FiniteDuration
import scala.concurrent.ExecutionContext.Implicits.global

/** This actor implements a high-level interaction layer with the nrf420 wireless chip driver. It encapsulates all the
  * low-level mechanics of working with driver and exb data streams, reducing them to a transactional principle. Each
  * request to this actor is a multiple-instance translation of the request-response type with a given timeout. For a
  * more detailed understanding of the work, see the description of commands and technical documentation for the driver
  * (WSD). */
object RfBridge {

  /** @param socket path to the unix domain socket control file (created by WSD)
    * @param maxReq maximum processing request at the time. If an attempt is made to send a request, this threshold is
    *               exceeded, then BridgeOverload is sent in response to the requester
    * @param cleanerTime cleaner timer interval. For more details see appropriate comment below
    * @param nap native api functionality proxy
    * @param system actor system proxy for testing
    * @param eventer events router actor
    * @param testMode test mode flag
    */
  def props(socket: String, maxReq: Int,
            cleanerTime: FiniteDuration = 1 second,
            nap: NativeApiProxy,
            eventer: ActorRef,
            cmdReplicator: ActorRef,
            system: ActorSystemProxy,
            testMode: Boolean = false) =
    Props(new RfBridge(socket, maxReq, cleanerTime, nap, eventer, cmdReplicator, system, testMode))

  object Commands {

    /** Send command to a some exb. May respond with: ExbResponse, ExbError, ExbAddrNotDefined, ExbUnreachable,
      * BridgeOverload, ExbBrokenResponse, TransactionTimeout, DriverError
      *
      * @param addr target network address
      * @param cmd command for exb
      * @param timeout transaction live time
      * @param meta metadata used to identify the final transaction message
      */
    case class ExbCommand(addr: Int, cmd: String, timeout: Int, meta: Any = "-")

    /** Set driver pipe matrix. For details see app documentation for understanding the principles of the wireless network
      * system. May respond with: SuccessDriverOperation, BadPipeMatrix, BadPipeMsb, TransactionTimeout, ChipNotRespond
      *
      * @param matrix pipe addresses matrix
      */
    case class SetPipeMatrix(matrix: PipeMatrix)

    /** Network pipe matrix, used with SetPipeMatrix message */
    case class PipeMatrix(
      p1targ: Int,
      p1self: Int,
      p2targ: Int,
      p2self: Int,
      p3targ: Int,
      p3self: Int,
      p4targ: Int,
      p4self: Int,
      p5targ: Int,
      p5self: Int
    ) {
      def toWsdPipeMatrix = {
        WsdPacket.PipeMatrix(p1targ, p1self, p2targ, p2self, p3targ, p3self, p4targ, p4self,p5targ, p5self)
      }
    }
  }

  object Responses {

    /** Correct response for ExbCommand message from exb
      *
      * @param payload what respond exb
      * @param meta transaction marker
      */
    case class ExbResponse(payload: String, meta: Any)

    /** Exb receive command initiated by ExbCommand, but some application error was occurred on the exb firmware level
      *
      * @param code error code
      * @param message error message
      * @param meta transaction marker
      */
    case class ExbError(code: Int, message: String, meta: Any)

    /** Command initiated by ExbResponse not be reach exb because target network address is not defined in the driver
      * routing table */
    case object ExbAddrNotDefined

    /** Command initiated by ExbResponse not be reach exb because it is does not respond on network level */
    case object ExbUnreachable

    /** Exb respond with broken packet */
    case object ExbBrokenResponse

    /** Unable to exec transaction because req table is full */
    case object BridgeOverload

    /** Driver does not complete transaction with specified timeout */
    case object TransactionTimeout

    /** Response for SetPipeMatrix transaction. Driver receive broken pipe matrix (too short or some else) */
    case object BadPipeMatrix

    /** Passed network address has bad msb (all msb in nrf network bust be unique) */
    case object BadPipeMsb

    /** Rf chip does not respond for driver commands */
    case object ChipNotRespond

    /** Some unknown driver error */
    case class DriverError(code: Int, message: String)

    /** Empty response for any success driver operation expect exb transmit transaction */
    case object SuccessDriverOperation
  }

  object Internals {

    /** In normal condition this message contain a WSD packet received by native layer from bridge read thread */
    case class WsdPayload(data: ByteString)

    /** Message purification of requests. This message is sent by the timer through the specified time intervals
      * defined by the cleanerTime value in the actor constructor. The task of the handler is to remove requests from
      * the pool, those response deadline from the WSD was passed. In addition to removing the request from
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

    val TtExbCommand = 0
    val TtSetPipeMatrix = 1
  }
}

class RfBridge(socket: String,
               maxReq: Int,
               cleanerTime: FiniteDuration,
               nap: NativeApiProxy,
               eventer: ActorRef,
               cmdReplicator: ActorRef,
               system: ActorSystemProxy,
               testMode: Boolean = false) extends Actor with StreamLogger with Timers {
  import RfBridge.Commands._
  import RfBridge.Responses._
  import RfBridge.Internals._

  setLogSourceName(s"RfBridge*${self.path.name}")
  setLogKeys(Seq("RfBridge"))
  implicit val logQualifier = LogEntryQualifier("static")

  /** The file descriptor of the client socket leading to the daemon */
  val sockfd = nap.unixDomainConnect(ByteString(socket).toArray)

  /** Current requests table */
  var reqTable = mutable.HashMap.empty[Int, RequestDescriptor]

  /** The thread of data reading from the socket. Its task is to get WSD packages by calling the native
    * unixDomainReadWsdPacket method. Upon receipt of the package, it is packaged in a WsdPayload message and sent
    * to the main actor for processing. */
  val reader = if (sockfd == -1) { //NOT TESTABLE
    logger.fatal(s"Could not open WSD socket '$socket'")
    Thread.sleep(3000)
    context.system.terminate()
    None
  } else {
    Some(new Runnable {
      var stop = false
      var alive = true

      override def run(): Unit = {
        while(!stop) {
          val packet = nap.unixDomainReadWsdPacket(sockfd, 1000)
          if (!stop)
            self ! WsdPayload(ByteString(packet))
        }
        alive = false
      }
    })
  }
  if (reader.isDefined) {
    new Thread(reader.get).start()
    logger.info("RfBridge actor is initialized")
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

      logger.debug("Rf bridge was stopped")
    }
  }

  override def receive = {

    /** See the message description */
    case ExbCommand(addr, cmd, timeout, meta) =>
      implicit val logQualifier = LogEntryQualifier("ExbCommand")

      val tid = if (reqTable.isEmpty) 1 else reqTable.maxBy(_._1)._1 + 1
      if (reqTable.size < maxReq) {
        logger.debug(s"Start exb command transaction '$tid' to address '${addr.toHexString.toUpperCase()}' with payload '$cmd' and timeout '$timeout' from the requestor '${sender.path.name}'")
        reqTable += tid -> RequestDescriptor(sender, TtExbCommand, System.currentTimeMillis() + timeout, meta)
        cmdReplicator ! CmdReplicator.Commands.Replic(addr, cmd, SourceBridge.RfBridge)
        nap.unixDomainWrite(sockfd, WsdTransmitPacket(tid, addr, ExbCommandPacket(tid, cmd)).toArray)
      } else {
        logger.warning(s"Unable to exec command transaction '$tid' to address '${addr.toHexString.toUpperCase()}' with payload '$cmd' and timeout '$timeout' from the requestor '${sender.path.name}'. Driver is overloaded")
        sender ! BridgeOverload
      }

    /** See the message description */
    case SetPipeMatrix(matrix) =>
      implicit val logQualifier = LogEntryQualifier("SetPipeMatrix")

      val tid = if (reqTable.isEmpty) 1 else reqTable.maxBy(_._1)._1 + 1

      logger.debug(s"Start set pipe matrix transaction '$tid' with matrix '$matrix' from the requestor '${sender.path.name}'")
      reqTable += tid -> RequestDescriptor(sender, TtSetPipeMatrix, System.currentTimeMillis() + ( if (testMode) 500 else 5000 ), "-")
      nap.unixDomainWrite(sockfd, WsdSetPipeMatrixPacket(tid, matrix.toWsdPipeMatrix).toArray)

    /** Event ack processing. This message generated by EventConfirmator and contain confirmation result */
    case EventConfirmator.Responses.EventAck(tid, addr, result) =>
      nap.unixDomainWrite(sockfd, WsdTransmitPacket(0, addr, ExbEventAckPacket(tid, if (result) 0 else 1)).toArray)

    /** See the message description */
    case WsdPayload(data) =>
      implicit val logQualifier = LogEntryQualifier("WsdPayload")
      val packet = WsdPacket(data)
      val tid = packet.tid

      val rd = reqTable.get(tid)
      if (rd.isDefined) { // If tid exist, this is transaction initiated by node
        val meta = reqTable(tid).meta
        reqTable -= tid

        rd.get.`type` match {
          case TtExbCommand =>
            packet match {
              case p: WsdReceivePacket =>
                p.exbp match {
                  case ep: ExbResponsePacket =>
                    logger.debug(s"Compete exb command transaction '${p.tid}' to address '${p.addr.toHexString.toUpperCase()}' with result '${ep.response}'")
                    rd.get.sender ! ExbResponse(ep.response, meta)
                  case ep: ExbErrorPacket =>
                    logger.info(s"Compete exb command transaction '${p.tid}' to address '${p.addr.toHexString.toUpperCase()}' with error '${ep.code}/${ep.message}'")
                    rd.get.sender ! ExbError(ep.code, ep.message, meta)
                  case _ =>
                    logger.warning(s"Compete exb command transaction '${p.tid}' to address '${p.addr.toHexString.toUpperCase()}' as exb broken response")
                    rd.get.sender ! ExbBrokenResponse
                }
              case p: WsdErrorPacket =>
                p.code match {
                  case WsdPacket.Constants.ERROR_ADDR_UNREACHABLE =>
                    logger.info(s"Compete exb command transaction '${p.tid}' as exb unreachable response")
                    rd.get.sender ! ExbUnreachable
                  case WsdPacket.Constants.ERROR_ADDR_NOT_DEFINED =>
                    logger.info(s"Compete exb command transaction '${p.tid}' as exb addr not defined response")
                    rd.get.sender ! ExbAddrNotDefined
                  case e =>
                    logger.warning(s"Compete exb command transaction '${p.tid}' as unknown driver error '$e/${p.message}'response")
                    rd.get.sender ! DriverError(e, p.message)
                }
            }

          case TtSetPipeMatrix =>
            packet match {
              case p: WsdResultPacket =>
                logger.debug(s"Compete set pipe matrix transaction '${p.tid}' with success result'")
                rd.get.sender ! SuccessDriverOperation

              case p: WsdErrorPacket =>
                p.code match {
                  case WsdPacket.Constants.ERROR_BROKEN_PIPE_MATRIX =>
                    logger.info(s"Compete set pipe matrix transaction '${p.tid}' as broken pipe matrix response")
                    rd.get.sender ! BadPipeMatrix
                  case WsdPacket.Constants.ERROR_BAD_PIPE_MSB =>
                    logger.info(s"Compete set pipe matrix transaction '${p.tid}' as bad pipe lsb response")
                    rd.get.sender ! BadPipeMsb
                  case WsdPacket.Constants.ERROR_CHIP_NOT_RESPOND=>
                    logger.info(s"Compete set pipe matrix transaction '${p.tid}' as error - chip does not respond")
                    rd.get.sender ! ChipNotRespond
                  case e =>
                    logger.warning(s"Compete set pipe matrix transaction '${p.tid}' as unknown driver error '$e/${p.message}'response")
                    rd.get.sender ! DriverError(e, p.message)
                }
            }

        }
      } else { // Received packet is a transaction initialized from outside
        packet match {
          case p: WsdReceivePacket =>
            p.exbp match {
              case ep: ExbEventPacket if ep.confirmed =>
                val event = HardwareEvent(ep.eid, ep.tid, p.addr, confirmed = true, ep.payload)
                logger.debug(s"Received confirmed event [${event.logPaste}]")
                system.actorOf(EventConfirmator.props(eventer)) ! EventConfirmator.Commands.Exec(event)
              case ep: ExbEventPacket if !ep.confirmed =>
                val event = HardwareEvent(ep.eid, ep.tid, p.addr, confirmed = false, ep.payload)
                logger.debug(s"Received non confirmed event [${event.logPaste}]")
                eventer ! Eventer.Commands.Receive(event)
              case _ =>
                logger.warning(s"Received broken external transaction packet")
            }
          case _ =>
        }
      }


    /** See the message description */
    case CleanerTick =>
      implicit val logQualifier = LogEntryQualifier("CleanerTick")
      reqTable = reqTable.foldLeft(mutable.HashMap.empty[Int, RequestDescriptor])((a, v) => {
        if (System.currentTimeMillis() > v._2.deadline) {
          logger.warning(s"The timeout for waiting for a response from the driver was reached on request for transaction '${v._1}'")
          v._2.sender ! TransactionTimeout
          a
        } else {
          a += v._1 -> v._2
        }
      })
  }

  /** This func is a component of WsdPayload message handler. See its comment for more details */
  def finishRequest(tid: Int)(f: RequestDescriptor => Any): Unit = {
    val rd = reqTable.get(tid)
    if (rd.isDefined) {
      reqTable -= tid
      f(rd.get)
    } else { //NOT TESTABLE
      logger.warning(s"Received response from driver with not registered tid '$tid'")
    }
  }
}
