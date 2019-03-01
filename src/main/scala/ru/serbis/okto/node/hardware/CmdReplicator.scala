package ru.serbis.okto.node.hardware

import akka.actor.{Actor, ActorRef, Props, Timers}
import ru.serbis.okto.node.events.{Eventer, HardwareEvent}
import ru.serbis.okto.node.hardware.CmdReplicator.Supply.{BridgeRefs, ReplicationPair, SourceBridge}
import ru.serbis.okto.node.hardware.CmdReplicator.Supply.SourceBridge.SourceBridge
import ru.serbis.okto.node.log.Logger.LogEntryQualifier
import ru.serbis.okto.node.log.StreamLogger
import ru.serbis.okto.node.utils.Counter

import scala.collection.mutable
import scala.concurrent.duration._

/**  This actor collects all passing commands across bridges and caches them if they are registered in the configuration
  *  under the cmdRecover item. In addition, the actor signs itself to receive hardware events with the code 100 - the
  *  device is running. When receiving this event, it sends previously cached commands to this device. Commands can be
  *  cached both global and addressable. The cmdRecover configuration is an array of value pairs, where the first element
  *  of the pair indicates the device address, and the second regular expression of the cached command. The device address
  *  can be replaced with the * character, then the cached commands will appear globally and will be sent to any device
  *  after the event 100. */
object CmdReplicator {

  /** @param replicMatrix work address - command matrix created by Runner from the configuration
    * @param eventer events controller reference
    * @param tm test mode flag
    */
  def props(replicMatrix: List[ReplicationPair], eventer: ActorRef, tm: Boolean = false) =
    Props(new CmdReplicator(replicMatrix, eventer, tm))


  object Commands {

    /** Message from a bridge for cashing some command
      *
      * @param addr device network address
      * @param cmd command
      * @param bridge actor from this command arrived
      */
    case class Replic(addr: Int, cmd: String, bridge: SourceBridge)

    /** Set bridges container */
    case class SetBridges(refs: BridgeRefs)
  }

  object Supply {

    /** Internal representation of the cmdRecover configuration
      *
      * @param addr device address. It may be None if in the config used * character
      * @param cmd command regular expressin
      */
    case class ReplicationPair(addr: Option[Int], cmd: String)

    /** Bridge types enumeration */
    object SourceBridge extends Enumeration {
      type SourceBridge = Value
      val RfBridge, SerialBridge = Value
    }

    /** Bridges refs container */
    case class BridgeRefs(serialBridge: ActorRef, rfBridge: ActorRef)
  }

  object Internals {

    /** Resend tick*/
    case object Tick

    /** Awt element - represent one replicate comand in progress */
    case class AwrElement(cmd: String, addr: Int, sourceBridge: SourceBridge, attempts: Int)
  }
}

class CmdReplicator(replicMatrix: List[ReplicationPair], eventer: ActorRef, tm: Boolean) extends Actor with StreamLogger with Timers {
  import CmdReplicator.Commands._
  import CmdReplicator.Internals._

  setLogSourceName(s"CmdReplicator")
  setLogKeys(Seq("CmdReplicator"))
  implicit val logQualifier = LogEntryQualifier("static")

  /** Max resend try's if commands was not delivered */
  val SendMaxAttempts = if (tm) 3 else 10

  /** Resend tick interval*/
  val TickInterval = if (tm) 0.5 second else 10 second

  /** Startup commands for specific addresses */
  var addrCash = Map.empty[(String, SourceBridge), Set[Int]]

  /** Bridge refs container */
  var bRefs: Option[BridgeRefs] = None

  /** Await for response map. Any send to bridge operation put an element to this map, and will try to resend
    * command from it each 5 second while deadline was reached or correct response from a bridge will be received */
  var awr = mutable.HashMap.empty[String, AwrElement]

  var metaCounter = Counter()

  timers.startPeriodicTimer(0, Tick, TickInterval)

  // Subscribe self for hardware event 100
  eventer.tell(Eventer.Commands.Subscribe(Some(100), self), ActorRef.noSender)

  logger.info("Command replicator was started")

  override def receive = {

    /** See the message description */
    case SetBridges(refs) => bRefs = Some(refs)

    /** See the message description */
    case Replic(addr, cmd, bridge) =>
      if (!addrCash.exists(v => v._1 == (cmd, bridge) && v._2.contains(addr))) {
        val rpc = replicMatrix.filter(v => {
          val sr = v.cmd.r.findFirstMatchIn(cmd)
          if (sr.isDefined) true
          else false
        })

        if (rpc.nonEmpty) {
          if (rpc.exists(v => v.addr.isEmpty)) { // If any global entry for search command exist
            val entry = addrCash.get((cmd, bridge))
            if (entry.isDefined) {
              val nset = entry.get ++ Set(addr)
              addrCash = addrCash + ((cmd, bridge) -> nset)
            } else
              addrCash = addrCash + ((cmd, bridge) -> Set(addr))
            logger.info(s"New global command replic was cached [cmd=$cmd, bridge=$bridge]")
          } else {
            val addrExist = rpc.find(v => {
              if (v.addr.isDefined) {
                if (v.addr.get == addr) true
                else false
              } else
                false
            })
            if (addrExist.isDefined) {
              val entry = addrCash.get((cmd, bridge))
              if (entry.isDefined) {
                val nset = entry.get ++ Set(addr)
                addrCash = addrCash + ((cmd, bridge) -> nset)
              } else
                addrCash = addrCash + ((cmd, bridge) -> Set(addr))
              logger.info(s"New address command replic was cached [cmd=$cmd, addr=${addr.toHexString.toUpperCase} bridge=$bridge]")
            }
          }
        }
      }

    /** Startup hardware event. Search commands in the caches and resend it to the source of the event */
    case e : HardwareEvent if e.eid == 100 =>
      logger.info(s"Received hardware startup event [addr=${e.addr.toHexString.toUpperCase}]")

      //Find existed works for addr
      if (!awr.exists(v => v._2.addr == e.addr)) {

        val addrSends = addrCash.filter(v => {
          if (v._2.contains(e.addr)) true
          else false
        })

        addrSends.foreach(v => {
          val as = v._2.filter(p => p == e.addr)
          as.foreach(_ => sendToBridge(v._1._2, v._1._1, e.addr))
        })
      }

    /** Resend tick. Check awt list and remove elements with overflowed attempts counter and resend command to device
      * fot not overflowed elements */
    case Tick =>
      val forRemove = awr.filter(v => v._2.attempts == 0)
      forRemove.foreach(v => {
        logger.debug(s"Replicated command to device was not delivered [cmd=${v._2.cmd}, addr=${v._2.addr.toHexString.toUpperCase}, bridge=${v._2.sourceBridge}]")
      })
      awr = awr.filter(v => !forRemove.contains(v._1)).map(v => v._1 -> v._2.copy(attempts = v._2.attempts - 1))
      awr.foreach(v => sendToBridge(v._2.sourceBridge, v._2.cmd, v._2.addr, retry = true))

    /** A block of message handlers from bridges that allow you to uniquely determine that the request was delivered to
      * the device. The task of these handlers is to remove these requests from the awt pool and log about successful
      * operation.*/
    case RfBridge.Responses.ExbResponse(_, meta) => completeSending(meta)
    case RfBridge.Responses.ExbError(_, _, meta) => completeSending(meta)
    case SerialBridge.Responses.ExbResponse(_, meta) => completeSending(meta)
    case SerialBridge.Responses.ExbError(_, _, meta) => completeSending(meta)

    case _ =>
  }

  def completeSending(meta: Any) = {
    val awrDef = awr.get(meta.asInstanceOf[String])
    if (awrDef.isDefined) {
      logger.debug(s"Replicated command to device was success delivered [cmd=${awrDef.get.cmd}, addr=${awrDef.get.addr.toHexString.toUpperCase}, bridge=${awrDef.get.sourceBridge}]")
      awr -= meta.asInstanceOf[String]
    }
  }

  /** Send early cached command to the bridge */
  def sendToBridge(bridge: SourceBridge, cmd: String, addr: Int, retry: Boolean = false): Unit = {
    if (bRefs.isDefined) {
      if (!retry) {
        logger.info(s"Replicated command to device was sent [cmd=$cmd, addr=${addr.toHexString.toUpperCase}, bridge=$bridge]")
        awr += (s"cmdr${metaCounter.get()}" -> AwrElement(cmd, addr, bridge, SendMaxAttempts))
      }
      bridge match {
        case SourceBridge.SerialBridge =>
          bRefs.get.serialBridge ! SerialBridge.Commands.ExbCommand(cmd, 3000)
        case SourceBridge.RfBridge =>
          bRefs.get.rfBridge ! RfBridge.Commands.ExbCommand(addr, cmd, 3000)
      }
    } else {
      logger.warning(s"Unable to replicate command to device after start because bridges container is empty [cmd=$cmd, addr=${addr.toHexString.toUpperCase}, bridge=$bridge]")
    }

  }
}
