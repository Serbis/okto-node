package ru.serbis.okto.node.syscoms.boot

import akka.actor.{ActorRef, Props}
import akka.util.ByteString
import ru.serbis.okto.node.common.FsmDefaults.{Data, State}
import ru.serbis.okto.node.common.ReachTypes.{ReachByteString, ReachVector}
import ru.serbis.okto.node.common.{CommandsUnion, Env}
import ru.serbis.okto.node.log.Logger.LogEntryQualifier
import ru.serbis.okto.node.runtime.{CmdExecutor, Stream}
import ru.serbis.okto.node.testut.{ActorSystemExpander, RealActorSystem}

import scala.concurrent.duration._

/** Performs operations with node's access system. Options:
  *
  * user  : performs operations with users. Has next suboptions
  *   add name=xxx password=xxx permissions=xxx,yyy groups=xxx,yyy  : create new user with specified params
  *   del _name_  : delete user with specified name
  *   list  : return all users definitions in the system as json array
  *   info _name_  : return single user definition
  *
  * group : performs operations with users. Has next suboptions
  *   add name=xxx permissions=xxx : create new group with specified params
  *   del _name_ : delete group with specified name
  *   list  : return all groups definitions in the system as json array
  *   info _name_  : return single group definition
  *
  */
object Boot {

  /** n/c */
  def props(env: Env, args: Vector[String], systemEx: ActorSystemExpander = new RealActorSystem, testMode: Boolean = false) =
    Props(new Boot(env, args, systemEx, testMode))

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

class Boot(env: Env, args: Vector[String], systemEx: ActorSystemExpander, testMode: Boolean) extends CmdExecutor(systemEx, testMode) {
  import Boot.Internals._
  import Boot.States._
  import Boot.StatesData._

  setLogSourceName(s"Boot*${self.path.name}")
  setLogKeys(Seq("Boot"))

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
            val fsm = systemEx.actorOf(BootInfo.props(args.tailOrEmpty, env, streams(1), streams(0)))
            fsm ! BootInfo.Commands.Exec
          case "--add" =>
            val fsm = systemEx.actorOf(BootAdd.props(args.tailOrEmpty, env, streams(1), streams(0)))
            fsm ! BootAdd.Commands.Exec
          case "--remove" =>
            val fsm = systemEx.actorOf(BootRemove.props(args.tailOrEmpty, env, streams(1), streams(0)))
            fsm ! BootRemove.Commands.Exec
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

