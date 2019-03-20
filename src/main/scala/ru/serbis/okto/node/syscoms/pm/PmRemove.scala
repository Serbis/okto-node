package ru.serbis.okto.node.syscoms.pm

import akka.actor.{ActorRef, FSM, Props}
import ru.serbis.okto.node.common.Env
import ru.serbis.okto.node.common.FsmDefaults.{Data, State}
import ru.serbis.okto.node.log.Logger.LogEntryQualifier
import ru.serbis.okto.node.log.StreamLogger
import ru.serbis.okto.node.reps.{ScriptsRep, UsercomsRep}

import scala.concurrent.duration._

/** Process manager sub-module for remove mode (--remove option). In this mode actor must modify the configuration and
  * remove script file from the disk.
  */
object PmRemove {

  /** @param nextArgs mode arguments
    * @param env node env object
    * @param stdIn command standard input
    * @param stdOut command standard output
    * @param testMode test mode
    */
  def props(nextArgs: Vector[String], env: Env, stdIn: ActorRef, stdOut: ActorRef, testMode: Boolean = false) =
    Props(new PmRemove(nextArgs, env, stdIn, stdOut, testMode))

  object States {

    /** see description of the state code */
    case object Idle extends State
    /** see description of the state code */
    case object WaitConfigRemove extends State
    /** see description of the state code */
    case object WaitScriptRemove extends State
  }

  object StatesData {

    /** n/c */
    case object Uninitialized extends Data
    /** n/c */
    case class InWaitConfigRemove(cmd: String) extends Data
    /** n/c */
    case object InWaitScriptRemove extends Data
  }

  object Commands {

    /** Start fsm work */
    case object Exec
  }
}

class PmRemove(nextArgs: Vector[String], env: Env, stdInt: ActorRef, stdOut: ActorRef, testMode: Boolean) extends FSM[State, Data] with StreamLogger {
  import PmRemove.Commands._
  import PmRemove.States._
  import PmRemove.StatesData._

  setLogSourceName(s"PmRemove*${self.path.name}")
  setLogKeys(Seq("PmRemove"))

  implicit val logQualifier = LogEntryQualifier("static")

  /** Original sender (Head Pm actor) */
  var orig = ActorRef.noSender

  startWith(Idle, Uninitialized)

  /** Staring mode. In this mode actor parse input arguments and send remove command to the usercoms repository */
  when(Idle, 5 second) {
    case Event(Exec, _) =>
      orig = sender()

      val name = nextArgs.headOption
      if (name.isEmpty) {
        orig ! Pm.Internals.Complete(20, "Command name is not presented")
        stop
      } else {
        if (name.get.contains("..") || name.get.contains("/") || name.get.contains(" ") || name.get.contains("\"")) {
          orig ! Pm.Internals.Complete(21, "Script name contain restricted symbols")
          stop
        } else {
          env.usercomsRep ! UsercomsRep.Commands.Remove(name.get)
          goto(WaitConfigRemove) using InWaitConfigRemove(name.get)
        }
      }

    //NOT TESTABLE
    case Event(StateTimeout, _) =>
      implicit val logQualifier = LogEntryQualifier("Idle_StateTimeout")
      logger.warning("FSM doest not run with expected timeout")
      stop
  }

  /** Waiting usercoms repository response */
  when(WaitConfigRemove, if (testMode) 0.5 second else 5 second) {

    /** Successfully removed configuration entry. Send request to the scripts repository for remove script file */
    case Event(UsercomsRep.Responses.Removed, data: InWaitConfigRemove) =>
      env.scriptsRep ! ScriptsRep.Commands.Remove(data.cmd)
      goto(WaitScriptRemove) using InWaitScriptRemove

    /** Configuration entry does not removed because script with specified name does not exist in the configuration */
    case Event(UsercomsRep.Responses.NotExist, _) =>
      orig ! Pm.Internals.Complete(22, "Command does not exist")
      stop

    /** Some io error was occurred at configuration writing procedure. Command terminates with failure */
    case Event(UsercomsRep.Responses.WriteError, _) =>
      orig ! Pm.Internals.Complete(23, "Configuration IO error")
      stop

    /** Repository does not respond with expected timeout. Command terminates with failure */
    case Event(StateTimeout, _) =>
      orig ! Pm.Internals.Complete(24, "Internal error type 0")
      stop
  }

  /** Waiting scripts repository response*/
  when(WaitScriptRemove, if (testMode) 0.5 second else 5 second) {

    /** Successfully removed script file. Command terminates with success */
    case Event(ScriptsRep.Responses.Removed, _) =>
      orig ! Pm.Internals.Complete(0, "Success")
      stop

    /**Some io error was occurred at script removing procedure. Command terminates with failure */
    case Event(ScriptsRep.Responses.WriteError, _) =>
      orig ! Pm.Internals.Complete(25, "Script IO error")
      stop

    /** Repository does not respond with expected timeout. Command terminates with failure */
    case Event(StateTimeout, _) =>
      orig ! Pm.Internals.Complete(26, "Internal error type 1")
      stop
  }

  initialize()
}