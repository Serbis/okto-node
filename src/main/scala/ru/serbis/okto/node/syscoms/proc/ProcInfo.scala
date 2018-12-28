package ru.serbis.okto.node.syscoms.proc

import akka.actor.{ActorRef, FSM, Props}
import ru.serbis.okto.node.common.Env
import ru.serbis.okto.node.common.FsmDefaults.{Data, State}
import ru.serbis.okto.node.common.NodeUtils.getOptions
import ru.serbis.okto.node.log.Logger.LogEntryQualifier
import ru.serbis.okto.node.log.StreamLogger
import ru.serbis.okto.node.runtime.Runtime

import scala.concurrent.duration._

object ProcInfo {

  /** @param nextArgs mode arguments
    * @param env node env object
    * @param stdIn command standard input
    * @param stdOut command standard output
    * @param testMode test mode
    */
  def props(nextArgs: Vector[String], env: Env, stdIn: ActorRef, stdOut: ActorRef, testMode: Boolean = false) =
    Props(new ProcInfo(nextArgs, env, stdIn, stdOut, testMode))

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

class ProcInfo(nextArgs: Vector[String], env: Env, stdInt: ActorRef, stdOut: ActorRef, testMode: Boolean) extends FSM[State, Data] with StreamLogger {
  import ProcInfo.States._
  import ProcInfo.StatesData._
  import ProcInfo.Commands._

  setLogSourceName(s"ProcInfo*${self.path.name}")
  setLogKeys(Seq("ProcInfo"))

  implicit val logQualifier = LogEntryQualifier("static")

  /** Original sender (Head Storage actor) */
  var orig = ActorRef.noSender

  startWith(Idle, Uninitialized)

  /** Send request to the runtime for full or partial list of processes definitions */
  when(Idle, 5 second) {
    case Event(Exec, _) =>
      orig = sender()

      val options = getOptions(nextArgs)

      if (options.contains("-p")) {
        val pid = options("-p")
        try {
          env.runtime ! Runtime.Commands.GetProcessesDefs(Some(List(pid.toInt)))
          logger.debug(s"Process info for pid '$pid' was requested")
          goto(WaitRuntimeResponse) using InWaitRuntimeResponse
        } catch {
          case _: Exception =>
            logger.warning(s"Bad -n option format, required number buf found '$pid'")
            orig ! Proc.Internals.Complete(10, "-p option is not a number")
            stop
        }

      } else {
        env.runtime ! Runtime.Commands.GetProcessesDefs(None)
        logger.debug(s"Process info for all pids was requested")
        goto(WaitRuntimeResponse) using InWaitRuntimeResponse
      }

    //NOT TESTABLE
    case Event(StateTimeout, _) =>
      implicit val logQualifier = LogEntryQualifier("Idle_StateTimeout")
      logger.error("FSM doest not run with expected timeout")
      stop
  }

  /** Receive response with processes defs from runtime. Create json string, write it to the stdOut and complete program
    * with code 0 */
  when(WaitRuntimeResponse, if (testMode) 0.5 second else 5 second) {
    case Event(Runtime.Responses.ProcessesDefs(defs), _) =>
      val p = "\""
      val json = s"[\n${
        defs.foldLeft("") {(a, m) =>
          s"$a\t{\n\t\t${p}pid$p : ${m.pid},\n\t\t${p}createTime$p : ${m.createTime},\n\t\t${p}command$p : $p${m.command}$p,\n\t\t${p}initiator$p : $p${m.initiator}$p,\n\t\t${p}owner$p : [$p${m.owner._1}$p, ${m.owner._2}]\n\t},\n"
        }.dropRight(2) + "\n"
      }]"

      logger.debug(s"List of '${defs.size}' processes definitions was responded")
      orig ! Proc.Internals.Complete(0, json)
      stop

    //NOT TESTABLE
    case Event(StateTimeout, _) =>
      implicit val logQualifier = LogEntryQualifier("WaitRuntimeResponse_StateTimeout")
      logger.error("Runtime actor response timeout")
      orig ! Proc.Internals.Complete(11, "Runtime timeout")
      stop
  }

  initialize()
}