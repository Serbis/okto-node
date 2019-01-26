package ru.serbis.okto.node.syscoms.proc

import akka.actor.{ActorRef, FSM, Props}
import ru.serbis.okto.node.common.Env
import ru.serbis.okto.node.common.FsmDefaults.{Data, State}
import ru.serbis.okto.node.common.NodeUtils.getOptions
import ru.serbis.okto.node.log.Logger.LogEntryQualifier
import ru.serbis.okto.node.log.StreamLogger
import ru.serbis.okto.node.runtime.Runtime

import scala.concurrent.duration._

object ProcSig {

  /** @param nextArgs mode arguments
    * @param env node env object
    * @param stdIn command standard input
    * @param stdOut command standard output
    * @param testMode test mode
    */
  def props(nextArgs: Vector[String], env: Env, stdIn: ActorRef, stdOut: ActorRef, testMode: Boolean = false) =
    Props(new ProcSig(nextArgs, env, stdIn, stdOut, testMode))

  object States {
    /** see description of the state code */
    case object Idle extends State
    /** see description of the state code */
    case object WaitRuntimeResponse extends State
  }

  object StatesData {
    /** n/c */
    case object Uninitialized extends Data
    /** n/c */
    case object InWaitRuntimeResponse extends Data
  }

  object Commands {

    /** Start fsm work */
    case object Exec
  }
}

class ProcSig(nextArgs: Vector[String], env: Env, stdInt: ActorRef, stdOut: ActorRef, testMode: Boolean) extends FSM[State, Data] with StreamLogger {
  import ProcSig.Commands._
  import ProcSig.States._
  import ProcSig.StatesData._

  setLogSourceName(s"ProcSig*${self.path.name}")
  setLogKeys(Seq("ProcSig"))

  implicit val logQualifier = LogEntryQualifier("static")

  /** Original sender (Head Storage actor) */
  var orig = ActorRef.noSender

  startWith(Idle, Uninitialized)

  /** Send SendSignal to the runtime with signal and pid from options */
  when(Idle, 5 second) {
    case Event(Exec, _) =>
      orig = sender()

      if (nextArgs.size == 2) {
        try {
          val pid = nextArgs(0).toInt
          val signal = nextArgs(1).toInt
          env.runtime ! Runtime.Commands.SendSignal(pid, signal)
          logger.debug(s"Sent action to the runtime for send signal '$signal' to process with pid '$pid'")

          goto(WaitRuntimeResponse) using InWaitRuntimeResponse
        } catch {
          case _: Exception =>
            logger.warning(s"Unable to cast pid or signal argument to Int")
            orig ! Proc.Internals.Complete(20, "Pid and signal must be a numbers")
            stop

        }
      } else {
        logger.warning(s"Expected 2 option but found '${nextArgs.size}'")
        orig ! Proc.Internals.Complete(21, s"Expected 2 option but found ${nextArgs.size}")
        stop
      }

    //NOT TESTABLE
    case Event(StateTimeout, _) =>
      implicit val logQualifier = LogEntryQualifier("Idle_StateTimeout")
      logger.warning("FSM doest not run with expected timeout")
      stop
  }

  /** Receive response from the runtime. If it is SignalSent then program complete with code 0. If if is
    * ProcessNotRegistered than program complete with error code */
  when(WaitRuntimeResponse, if (testMode) 0.5 second else 5 second) {
    case Event(Runtime.Responses.SignalSent, _) =>
      logger.debug(s"Signal was successfully processed by runtime")
      orig ! Proc.Internals.Complete(0, "OK")
      stop

    case Event(Runtime.Responses.ProcessNotRegistered, _) =>
      logger.warning(s"Signal does not processed by runtime because process does not exist")
      orig ! Proc.Internals.Complete(22, "Process does not exist")
      stop

    //NOT TESTABLE
    case Event(StateTimeout, _) =>
      implicit val logQualifier = LogEntryQualifier("WaitRuntimeResponse_StateTimeout")
      logger.error("Runtime actor response timeout")
      orig ! Proc.Internals.Complete(23, "Runtime timeout")
      stop
  }

  initialize()
}