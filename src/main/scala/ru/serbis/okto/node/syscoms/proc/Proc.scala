package ru.serbis.okto.node.syscoms.proc

import akka.actor.{ActorRef, Props}
import akka.util.ByteString
import ru.serbis.okto.node.common.FsmDefaults.{Data, State}
import ru.serbis.okto.node.common.ReachTypes.{ReachByteString, ReachVector}
import ru.serbis.okto.node.common.{CommandsUnion, Env}
import ru.serbis.okto.node.log.Logger.LogEntryQualifier
import ru.serbis.okto.node.runtime.{CmdExecutor, Stream}
import ru.serbis.okto.node.testut.{ActorSystemExpander, RealActorSystem}
import scala.concurrent.duration._

/** Performs operations with running processes. Options
  * --info:   return info about running processes as json. If not args specified, implies that information is requested
  *           on all processes in the system. If option -p [Int] was specified, will be returned precess info for
  *           specified pid
  *
  * --sig:   send signal to the process. First number after option determine pid, second signal
  *
  */
object Proc {
  /** n/c */
  def props(env: Env, args: Vector[String], systemEx: ActorSystemExpander = new RealActorSystem, testMode: Boolean = false) =
    Props(new Proc(env, args, systemEx, testMode))

  object States {
    /** see description of the state code */
    case object Idle extends State

    /** see description of the state code */
    case object WaitResult extends State

    /** see description of the state code */
    case object WaitStdOutResponse extends State

    /** see description of the state code */
    case object CompleteExecution extends State
  }

  object StatesData {
    /** n/c */
    case object Uninitialized extends Data

    /** n/c */
    case class InCompleteExecution() extends Data
  }

  object Internals {

    /** Self sent message in state CompleteExecution (or it may be send be inner fsm). For detail, see state description
      *
      * @param code exit code
      * @param message exit message
      */
    case class Complete(code: Int, message: String)
  }
}

class Proc(env: Env, args: Vector[String], systemEx: ActorSystemExpander, testMode: Boolean) extends CmdExecutor(systemEx, testMode) {
  import Proc.States._
  import Proc.StatesData._
  import Proc.Internals._

  setLogSourceName(s"Proc*${self.path.name}")
  setLogKeys(Seq("Proc"))

  var process = ActorRef.noSender
  var streams = Map.empty[Int, ActorRef]

  startWith(Idle, Uninitialized)

  logger.debug("Command logic initialized")

  /** Starting state. It parallels the operation of the command, depending on the value of the first argument. By
    * parallelization is meant the launch of specific actors of option handlers. These handlers perform some work, and
    * then send a message to the head actor Complete, which should complete the work of the command */
  when(Idle, 5 second) {
    case Event(req: CommandsUnion.Commands.Run, _) =>
      implicit val logQualifier = LogEntryQualifier("Idle_Run")
      process = req.process
      streams = req.streams

      if (args.nonEmpty) {
        args.head match {
          case "--info" =>
            val fsm = systemEx.actorOf(ProcInfo.props(args.tailOrEmpty, env, streams(1), streams(0)))
            fsm ! ProcInfo.Commands.Exec
          case "--sig" =>
            val fsm = systemEx.actorOf(ProcSig.props(args.tailOrEmpty, env, streams(1), streams(0)))
            fsm ! ProcSig.Commands.Exec
          case e =>
            self ! Complete(1, s"Unexpected option '$e'")
        }

        goto(CompleteExecution) using InCompleteExecution()
      } else {
        self ! Complete(1, "Not enough arguments")
        goto(CompleteExecution) using InCompleteExecution()
      }


    //NOT TESTABLE
    case Event(StateTimeout, _) =>
      implicit val logQualifier = LogEntryQualifier("Idle_StateTimeout")
      logger.warning("FSM doest not run with expected timeout")
      stop
  }

  /** The final execution point of the command. In this state, only one message is accepted: Complete. It is directed
    * by the actor to herself before going into this state. The message handler writes the message to the standard
    * output with the terminators eoi-eof. Then the process terminates. */
  when(CompleteExecution, 60 second) {
    case Event(Complete(code, message), _) =>
      implicit val logQualifier = LogEntryQualifier("CompleteExecution_Complete")
      logger.info(s"Command 'storage ${args.slice(1, args.size).toSpacedString}' completed with code $code ${if (code != 0) s" / $message" else ""}")
      streams(0).tell(Stream.Commands.WriteWrapped(ByteString(message).eoi.eof.exit(code)), ActorRef.noSender)
      process ! CommandsUnion.Responses.ExecutorFinished(code)
      stop

    //NOT TESTABLE
    case Event(StateTimeout, _) =>
      implicit val logQualifier = LogEntryQualifier("CompleteExecution_StateTimeout")
      logger.warning("Very strange timeout")
      streams(0).tell(Stream.Commands.WriteWrapped(ByteString("Internal error 2")), ActorRef.noSender)
      streams(0).tell(Stream.Commands.WriteWrapped(ByteString().eof.exit(1)), ActorRef.noSender)
      process ! CommandsUnion.Responses.ExecutorFinished(1)
      stop
  }

  initialize()
}

