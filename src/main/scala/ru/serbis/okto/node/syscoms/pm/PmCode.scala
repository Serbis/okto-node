package ru.serbis.okto.node.syscoms.pm

import akka.actor.{ActorRef, FSM, Props}
import ru.serbis.okto.node.common.Env
import ru.serbis.okto.node.common.FsmDefaults.{Data, State}
import ru.serbis.okto.node.log.Logger.LogEntryQualifier
import ru.serbis.okto.node.log.StreamLogger
import ru.serbis.okto.node.reps.ScriptsRep

import scala.concurrent.duration._

/** Process manager sub-module for code mode (--code option). In this mode actor must get script code from the disk and
  * return it to the requestor.
  */
object PmCode {

  /** @param nextArgs mode arguments
    * @param env node env object
    * @param stdIn command standard input
    * @param stdOut command standard output
    * @param testMode test mode
    */
  def props(nextArgs: Vector[String], env: Env, stdIn: ActorRef, stdOut: ActorRef, testMode: Boolean = false) =
    Props(new PmCode(nextArgs, env, stdIn, stdOut, testMode))

  object States {
    /** see description of the state code */
    case object Idle extends State
    /** see description of the state code */
    case object WaitScriptCode extends State
  }

  object StatesData {
    /** n/c */
    case object Uninitialized extends Data
    /** n/c */
    case object InWaitScriptCode extends Data
  }

  object Commands {

    /** Start fsm work */
    case object Exec
  }
}

class PmCode(nextArgs: Vector[String], env: Env, stdInt: ActorRef, stdOut: ActorRef, testMode: Boolean) extends FSM[State, Data] with StreamLogger {
  import PmCode.Commands._
  import PmCode.States._
  import PmCode.StatesData._

  setLogSourceName(s"PmCode*${self.path.name}")
  setLogKeys(Seq("PmCode"))

  implicit val logQualifier = LogEntryQualifier("static")

  /** Original sender (Head Pm actor) */
  var orig = ActorRef.noSender

  startWith(Idle, Uninitialized)

  /** Staring mode. In this mode actor send request to get script code from scripts repository */
  when(Idle, 5 second) {
    case Event(Exec, _) =>
      orig = sender()

      val name = nextArgs.headOption
      if (name.isEmpty) {
        orig ! Pm.Internals.Complete(40, "Command name is not presented")
        stop
      } else {
        if (name.get.exists(v => v == '.' || v == '/' || v == ' ')) {
          orig ! Pm.Internals.Complete(41, "Script name contain restricted symbols")
          stop
        } else {
          env.scriptsRep ! ScriptsRep.Commands.GetScript(s"${name.get}.js")
          goto(WaitScriptCode) using InWaitScriptCode
        }
      }

    //NOT TESTABLE
    case Event(StateTimeout, _) =>
      implicit val logQualifier = LogEntryQualifier("Idle_StateTimeout")
      logger.warning("FSM doest not run with expected timeout")
      stop
  }

  /** Waiting response from scripts repository */
  when(WaitScriptCode,  if (testMode) 0.5 second else 5 second) {

    /** Repository return script code. Command terminates with success result and print received code */
    case Event(ScriptsRep.Responses.Script(code), _) =>
      orig ! Pm.Internals.Complete(0, code)
      stop

    /** Script not found on the disk (or may not be read) */
    case Event(ScriptsRep.Responses.ScriptNotFound, _) =>
      orig ! Pm.Internals.Complete(42, "Command does not exist")
      stop

    /** Repository does not respond with expected timeout */
    case Event(StateTimeout, _) =>
      orig ! Pm.Internals.Complete(43, "Internal error type 0")
      stop
  }

  initialize()
}