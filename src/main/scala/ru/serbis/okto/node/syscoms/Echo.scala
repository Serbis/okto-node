package ru.serbis.okto.node.syscoms

import akka.actor.{ActorRef, FSM, Props}
import akka.util.ByteString
import ru.serbis.okto.node.common.{CommandsUnion, Env}
import ru.serbis.okto.node.common.FsmDefaults.{Data, State}
import ru.serbis.okto.node.log.Logger.LogEntryQualifier
import ru.serbis.okto.node.log.StreamLogger
import ru.serbis.okto.node.runtime.{CmdExecutor, Stream}
import ru.serbis.okto.node.common.ReachTypes.ReachByteString
import ru.serbis.okto.node.runtime.Stream.Responses.{BufferIsFull, Written}
import ru.serbis.okto.node.testut.{ActorSystemExpander, RealActorSystem}
import ru.serbis.okto.node.runtime.StreamControls._
import ru.serbis.okto.node.common.ReachTypes.ReachVector

import scala.concurrent.duration._

/** Implementation of the logic of the system command
  *
  * Name : echo
  * Args: text - returned text
  * Action: Raises the contents of argument 0 or a message about the lack of arguments to stream_0.
  * Exit codes: ???
  */
object Echo {
  /** n/c */
  def props(env: Env, args: Vector[String], systemEx: ActorSystemExpander = new RealActorSystem, testMode: Boolean = false) =
    Props(new Echo(env, args, systemEx, testMode))

  object States {
    /** see description of the state code */
    case object Idle extends State

    /** see description of the state code */
    case object WaitStdOutResponse extends State
  }

  object StatesData {
    /** n/c */
    case object Uninitialized extends Data

    /** @param success the success of the command
      * @param process process actor reference
      */
    case class WaitingStdOutResponse(success: Boolean, process: ActorRef) extends Data

  }
}

class Echo(env: Env, args: Vector[String], systemEx: ActorSystemExpander, testMode: Boolean) extends CmdExecutor(systemEx, testMode) {
  import Echo.States._
  import Echo.StatesData._

  setLogSourceName(s"Echo*${self.path.name}")
  setLogKeys(Seq("Echo"))

  startWith(Idle, Uninitialized)

  logger.debug("Command logic initialized")

  /** Starting state. It checks for the presence of a zero argument. If it has a place to be, send it to stream_0.
    * If the command completely lacks arguments, then a message is sent to stream_0 about the lack of arguments.
    * After this, the actor goes into the expectation of a response from the stream_0.
    */
  when(Idle, 5 second) {
    case Event(req: CommandsUnion.Commands.Run, _) =>
      implicit val logQualifier = LogEntryQualifier("Idle_Run")

      if (args.nonEmpty) {
        req.streams(0) ! Stream.Commands.WriteWrapped(ByteString(s"${args(0)}") ++ ByteString(Array(EOF)))
        goto(WaitStdOutResponse) using WaitingStdOutResponse(success = true, req.process)
      } else {
        req.streams(0) ! Stream.Commands.WriteWrapped(ByteString(s"Not enough arguments") ++ ByteString(Array(EOF)))
        goto(WaitStdOutResponse) using WaitingStdOutResponse(success = false, req.process)
      }

    case Event(StateTimeout, _) =>
      implicit val logQualifier = LogEntryQualifier("Idle_StateTimeout")
      logger.warning("FSM doest not run with expected timeout")
      stop
  }

  /** The response received from stream_0. If the data was successfully written to the stream, the command
    * process is notified of the exit code 0 or 1, depending on the results of the initial stage. If the data
    * is not written to the stream due to its overflow, the command process is notified about the exit code 2.
    * If the stream does not respond within the specified timeout, the command process is notified of the exit
    * code 3.
    */
  when(WaitStdOutResponse, if (testMode) 1 second else 5 second) {
    case Event(Written, data: WaitingStdOutResponse) =>
      implicit val logQualifier = LogEntryQualifier("WaitStdOutResponse_Written")
      if (data.success) {
        logger.info(s"Command 'echo ${args.toSpacedString}' completed with code 0")
        data.process ! CommandsUnion.Responses.ExecutorFinished(0)
      } else {
        logger.warning(s"Command 'echo ${args.toSpacedString}' completed with code 1 / Not enough arguments")
        data.process ! CommandsUnion.Responses.ExecutorFinished(1)
      }
      stop

    case Event(BufferIsFull, data: WaitingStdOutResponse) =>
      implicit val logQualifier = LogEntryQualifier("WaitStdOutResponse_BufferIsFull")
      logger.warning(s"Command 'echo ${args.toSpacedString}' completed with code 2 / Stream_0 buffer is full")
      data.process ! CommandsUnion.Responses.ExecutorFinished(2)
      stop

    case Event(StateTimeout, data: WaitingStdOutResponse) =>
      implicit val logQualifier = LogEntryQualifier("WaitStdOutResponse_StateTimeout")
      logger.error(s"Command 'echo ${args.toSpacedString}' completed with code 3 / Stream_0 does not respond")
      data.process ! CommandsUnion.Responses.ExecutorFinished(3)
      stop
  }

  initialize()
}

