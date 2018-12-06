package ru.serbis.okto.node.runtime

import java.lang

import akka.actor.{Actor, ActorRef, FSM, Props}
import ru.serbis.okto.node.common.Env
import ru.serbis.okto.node.common.FsmDefaults.{Data, State}
import ru.serbis.okto.node.log.Logger.LogEntryQualifier
import ru.serbis.okto.node.log.StreamLogger
import ru.serbis.okto.node.runtime.ProcessConstructor.Commands.Exec
import ru.serbis.okto.node.runtime.ProcessConstructor.Responses.ProcessDef

import scala.concurrent.duration._

object ProcessConstructor {
  /** n/c */
  def props(env: Env, testMode: Boolean = false) = Props(new ProcessConstructor(env, testMode))

  object States {
    /** see description of the state code */
    case object Idle extends State

    /** see description of the state code */
    case object WaitStreamAttachments extends State

    /** see description of the state code */
    case object WaitPid extends State
  }

  object StatesData {
    /** n/c */
    case object Uninitialized extends Data

    /** @param count attached response counter
      * @param executor command logic executor
      * @param streams map with command IO streams
      * @param orig original sender
      */
    case class WaitingStreamAttachments(count: Int, initiator: ActorRef, executor: ActorRef, streams: Map[Int, ActorRef], orig: ActorRef, pid: Int) extends Data

    /** @param executor class of an actor that implements the program logic of a command
      * @param orig original sender
      */
    case class WaitingPid(initiator: ActorRef, executor: ActorRef,  orig: ActorRef, stdOutReceiver: ActorRef) extends Data
  }

  object Commands {

    /** @param executor class of an actor that implements the program logic of a command
      * @param initiator actor is the recipient of the StdOut and exit code of the command
      */
    case class Exec(executor: ActorRef, initiator: ActorRef)
  }

  object Responses {

    /** Successful completion of the process initialization process
      *
      * @param ref process reference
      */
    case class ProcessDef(ref: ActorRef, pid: Int, streams: Map[Int, ActorRef])

    /** Error response */
    case object Error
  }
}

class ProcessConstructor(env: Env, testMode: Boolean) extends FSM[State, Data] with StreamLogger {
  import ProcessConstructor.States._
  import ProcessConstructor.StatesData._

  setLogSourceName(s"ProcessConstructor*${self.path.name}")
  setLogKeys(Seq("ProcessConstructor"))

  implicit val logQualifier = LogEntryQualifier("static")

  startWith(Idle, Uninitialized)

  logger.debug("Start process construction...")

  /** Starting state. A request is made to runtime for pid reservation.
    */
  when(Idle, 5 second) {
    case Event(req: Exec, _) =>
      implicit val logQualifier = LogEntryQualifier("Idle_Exec")
      env.runtime ! Runtime.Commands.ReservePid
      goto(WaitPid) using WaitingPid(req.initiator, req.executor, sender(), req.initiator)

    case Event(StateTimeout, _) =>
      implicit val logQualifier = LogEntryQualifier("Idle_StateTimeout")
      logger.warning("FSM doest not start with expected timeout")
      stop
  }

  /** It is expected to receive a pid from the runtime. In it creates the actor of the executor of program logic. I / O command streams are created.
    * For the standard output stream, the initiator of the command start is assigned as the data receiver. For the
    * standard input stream, the program logic executor is assigned as the data receiver.
    */
  when(WaitPid, if (testMode) 1 second else 5 second) {
    case Event(pid: Runtime.Responses.Pid, data: WaitingPid) =>
      implicit val logQualifier = LogEntryQualifier("WaitPid_Pid")

      val streams = Map (
        0 -> context.system.actorOf(Stream.props(100000, pid.id), s"Stream_${pid.id}_0"),
        1 -> context.system.actorOf(Stream.props(100000, pid.id), s"Stream_${pid.id}_1")
      )
      //streams(0) ! Stream.Commands.Attach(data.stdOutReceiver)
      streams(1) ! Stream.Commands.Attach(data.executor)
      goto(WaitStreamAttachments) using WaitingStreamAttachments(2, data.initiator, data.executor, streams, data.orig, pid.id)

    case Event(StateTimeout, data: WaitingPid) =>
      implicit val logQualifier = LogEntryQualifier("WaitPid_StateTimeout")
      logger.error("Runtime pid reservation response does not receive with expected timeout")
      data.orig ! ProcessConstructor.Responses.Error
      stop
  }

  /** It is expected that recipients will receive replies from the I / O streams. When all the answers are received,
    * a new process actor is created. The result is sent to the initiator of the process creation.
    */
  when(WaitStreamAttachments, if (testMode) 1 second else 5 second) {
    case Event(Stream.Responses.Attached, data: WaitingStreamAttachments) =>
      //if (data.count <= 1) {
        val process = context.system.actorOf(Process.props(env, data.initiator, data.executor, data.streams, data.pid), s"process_${data.pid}")
        logger.debug(s"Process with pid '${data.pid}' was constructed")
        data.orig ! ProcessDef(process, data.pid, data.streams)
        stop
      //} else {
      //  stay using data.copy(count = data.count - 1)
      //}

    case Event(StateTimeout, data: WaitingStreamAttachments) =>
      implicit val logQualifier = LogEntryQualifier("WaitStreamAttachments_StateTimeout")
      logger.error("Stream attach response does not receive with expected timeout")
      data.orig ! ProcessConstructor.Responses.Error
      stop
  }

  initialize()
}

