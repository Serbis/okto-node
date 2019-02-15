package ru.serbis.okto.node.hardware.packets

import akka.actor.{ActorRef, FSM, Props}
import ru.serbis.okto.node.common.FsmDefaults.{Data, State}
import ru.serbis.okto.node.events.{Eventer, HardwareEvent}
import ru.serbis.okto.node.log.Logger.LogEntryQualifier
import ru.serbis.okto.node.log.StreamLogger

import scala.concurrent.duration._

/**
  * This actor is used by bridges to handle confirmed events. Its task is to register an event in the event controller,
  * and then send the bridge to indicate to the event initiator of the confirmation result response.
  */
object  EventConfirmator {

  /** @param eventer events processor actor
    * @param testMode test mode flag
    */
  def props(eventer: ActorRef, testMode: Boolean = false): Props =
    Props(new EventConfirmator(eventer, testMode))

  object States {
    /** see description of the state code */
    case object Idle extends State

    /** see description of the state code */
    case object WaitEventerResponse extends State
  }

  object StatesData {
    /** n/c */
    case object Uninitialized extends Data

    /** n/c */
    case class InWaitEventerResponse(event: HardwareEvent) extends Data
  }

  object Commands {

    /** n/c */
    case class Exec(event: HardwareEvent)
  }

  object Responses {

    /** This message is sent to the bridge as a result of acknowledging the event. If the bridge supports handling of
      * confirmed events, it should be able to process this message.
      *
      * @param tid event transaction identifier
      * @param addr address from event was received
      * @param result result of confirmation, true if all ok, or false if event does not processed
      */
    case class EventAck(tid: Int, addr: Int, result: Boolean)
  }
}

class EventConfirmator(eventer: ActorRef, testMode: Boolean = false) extends FSM[State, Data] with StreamLogger {
  import EventConfirmator.Commands._
  import EventConfirmator.States._
  import EventConfirmator.StatesData._
  import EventConfirmator.Responses._

  setLogSourceName(s"EventConfirmator*${self.path.name}")
  setLogKeys(Seq("EventConfirmator"))

  implicit val logQualifier = LogEntryQualifier("static")

  var orig = ActorRef.noSender

  startWith(Idle, Uninitialized)

  /** Send event registration request to the eventer and go to wait response */
  when(Idle, 5 second) {
    case Event(Exec(event), _) =>
      implicit val logQualifier = LogEntryQualifier("Idle_Exec")
      orig = sender()

      eventer ! Eventer.Commands.Receive(event)
      goto(WaitEventerResponse) using InWaitEventerResponse(event)

    //NOT TESTABLE
    case Event(StateTimeout, _) =>
      implicit val logQualifier = LogEntryQualifier("Idle_StateTimeout")
      logger.warning("Exec command wait timeout")
      stop
  }

  /** Wait response from eventer and complete fsm execution with EventAck to the originator */
  when(WaitEventerResponse, if (testMode) 0.5 second else 5 second) {
    case Event(Eventer.Responses.Delivered(_), InWaitEventerResponse(event)) =>
      implicit val logQualifier = LogEntryQualifier("WaitEventerResponse_Delivered")
      log.debug(s"Eventer accept event, event completed as confirmed [${event.logPaste}]")
      orig ! EventAck(event.id, event.addr, result = true)
      stop

    case Event(Eventer.Responses.NotDelivered(_, reason), InWaitEventerResponse(event)) =>
      implicit val logQualifier = LogEntryQualifier("WaitEventerResponse_NotDelivered")
      log.info(s"Eventer does accept event, event completed as not confirmed [${event.logPaste}]")
      orig ! EventAck(event.id, event.addr, result = false)
      stop

    case Event(StateTimeout,  InWaitEventerResponse(event)) =>
      implicit val logQualifier = LogEntryQualifier("WaitEventerResponse_StateTimeout")
      log.warning(s"Eventer does not respond, event completed as not confirmed [${event.logPaste}]")
      orig ! EventAck(event.id, event.addr, result = false)
      stop
  }

  initialize()
}
