package ru.serbis.okto.node.runtime.senv

import java.nio.{ByteBuffer, ByteOrder}
import java.util.concurrent.{LinkedBlockingQueue, Semaphore}

import akka.actor.ActorRef
import ru.serbis.okto.node.events.{Eventer, HardwareEvent, SystemEvent}
import ru.serbis.okto.node.log.Logger.LogEntryQualifier
import ru.serbis.okto.node.log.StreamLogger

import scala.concurrent.duration._
import akka.pattern.ask

import scala.concurrent.Await

/** Script environment object for event management
  *
  * @param eventer events controller
  * @param executor script executor ref
  */
class VEvents(eventer: ActorRef, executor: ActorRef) extends StreamLogger {

  /** This collection is used to implement event filtering by addresses. See subscribe and unsubscribe descriptions */
  var hwEventsFilter = Map.empty[Int, Set[Int]]

  /** Queue of received events. The queue replenished be executor witch receive events from events controller */
  val eventsQueue = new LinkedBlockingQueue[SystemEvent]

  /** Semaphore lock wait event. Initially in unlocked state. Designed to use receive methods in blocking mode. Immediately
    * after entering the method, it will attempt to take a semaphore. He will be free only if the executor released him when
    * he received a new event. After capturing an event from the queue, the receive code will translate it back into
    * captured mode.*/
  val recvSem = new Semaphore(1)

  recvSem.acquire() // Lock receive semaphore. At this point sem is always free

  setLogSourceName(s"VEvents*${System.currentTimeMillis()}")
  setLogKeys(Seq("VEvents"))

  implicit val logQualifier = LogEntryQualifier("static")

  /** Subscribe script for hardware events
    *
    * @param eid event code
    * @param addr source network address
    */
  def subHardEvent(eid: Int, addr: Int): Boolean = {
    val exist = hwEventsFilter.find(v => v._1 == eid)

    if (exist.isDefined) {
      if (!exist.get._2.contains(addr))
        hwEventsFilter = hwEventsFilter + (eid -> (exist.get._2 + addr))
      true
    } else {
      val result = try {
        Await.result(eventer.ask(Eventer.Commands.Subscribe(Some(eid), executor))(3 second), 3 second) match {
          case Eventer.Responses.Subscribed =>
            logger.debug(s"Script subscribed for hardware event [eid=$eid, add=$addr]")
            true
          case m =>
            logger.warning(s"Script not subscribed for hardware event because eventer respond with unexpected message [eid=$eid, addr=$addr, m=$m]")
            false
        }
      } catch {
        case _: Throwable =>
          logger.warning(s"Script not subscribed for hardware event because eventer does not respond [eid=$eid, addr=$addr]")
          false
      }

      if (result)
        hwEventsFilter = hwEventsFilter + (eid -> Set(addr))

      result
    }
  }

  /** Unsubscribe script for hardware events
    *
    * @param eid event code
    * @param addr source network address
    */
  def unsubHardEvent(eid: Int, addr: Int): Boolean = {
    val exist = hwEventsFilter.find(v => v._1 == eid && v._2.contains(addr))
    if (exist.isDefined) {
      val fe = exist.get._2 - addr
      if (fe.isEmpty) {
        val result = try {
          Await.result(eventer.ask(Eventer.Commands.Unsubscribe(Some(eid), executor))(3 second), 3 second) match {
            case Eventer.Responses.Unsubscribed =>
              logger.debug(s"Script unsubscribed from hardware event [eid=$eid, add=$addr]")
              true
            case m =>
              logger.warning(s"Script not unsubscribed for hardware event because eventer respond with unexpected message [eid=$eid, addr=$addr, m=$m]")
              false
          }
        } catch {
          case _: Throwable =>
            logger.warning(s"Script not unsubscribed for hardware event because eventer does not respond [eid=$eid, addr=$addr]")
            false
        }

        if (result)
          hwEventsFilter = hwEventsFilter - eid

        result
      } else {
        hwEventsFilter = hwEventsFilter + (eid -> fe)
        true
      }
    } else {
      true
    }
  }

  /** Receive event from events queue.
    *
    * @param blocked if this flag is set, call will be blocked until new event will be received.
    *                If with this flag function return null, it will be indicate, that event
    *                waiting was break be signal
    * @return event wrapper
    */
  def receive(blocked: Boolean): EventWrapper = {
    def wrap(e: SystemEvent) = e match {
      case x: HardwareEvent =>
        logger.debug(s"Received new hardware event [${x.logPaste}]")
        EventWrapper(0, HardwareEventWrapper(x.eid, x.addr, x.data.utf8String))

      case x =>
        logger.warning(s"Received unsupported event type [event=$x]")
        null
    }

    if (eventsQueue.size() > 0) {
      recvSem.acquire() // At this point sem is always free
      wrap(eventsQueue.poll())
    } else {
      if (blocked) {
        val int = try {
          recvSem.acquire()
          false
        } catch {
          case _: InterruptedException =>
            true
        }
        if (int) { // If sem waiting was interrupted, this is a signal
          logger.debug(s"Blocked event receive was interrupted")
          null
        } else
          wrap(eventsQueue.poll())
      } else {
        null
      }
    }
  }

  /** Attention - for internal use only
    * This function realize events filtering logic and used be executor for enqueuing events
    */
  def __putEvent(event: SystemEvent) = {
    event match  {
      case e: HardwareEvent =>
        val exist = hwEventsFilter.find(v => v._1 == e.eid && v._2.contains(e.addr))
        if (exist.isDefined) {
          if (recvSem.availablePermits() == 0)
            recvSem.release()
          eventsQueue.offer(e)
        }
      case _ =>
    }
  }
}

abstract class EventContainer
/** Event wrapper for script code
  *
  * @param `type` type of the event, may be:
  *               0 - hardware event
  * @param event native event object
  */
case class EventWrapper(`type`: Int, event: EventContainer)

case class HardwareEventWrapper(eid: Int, addr: Int, payload: String) extends EventContainer
