package ru.serbis.okto.node.syscoms.boot

import akka.actor.{ActorRef, FSM, Props}
import ru.serbis.okto.node.common.Env
import ru.serbis.okto.node.common.FsmDefaults.{Data, State}
import ru.serbis.okto.node.common.NodeUtils.getOptions
import ru.serbis.okto.node.log.Logger.LogEntryQualifier
import ru.serbis.okto.node.log.StreamLogger
import ru.serbis.okto.node.reps.{BootRep, StorageRep}

import scala.concurrent.duration._

object BootAdd {

  /** @param nextArgs mode arguments
    * @param env node env object
    * @param stdIn command standard input
    * @param stdOut command standard output
    * @param testMode test mode
    */
  def props(nextArgs: Vector[String], env: Env, stdIn: ActorRef, stdOut: ActorRef, testMode: Boolean = false) =
    Props(new BootAdd(nextArgs, env, stdIn, stdOut, testMode))

  object States {
    /** see description of the state code */
    case object Idle extends State
    /** see description of the state code */
    case object WaitRepositoryResult extends State
  }

  object StatesData {
    /** n/c */
    case object Uninitialized extends Data
    /** n/c */
    case object InWaitRepositoryResult extends Data
  }

  object Commands {

    /** Start fsm work */
    case object Exec
  }
}

class BootAdd(nextArgs: Vector[String], env: Env, stdInt: ActorRef, stdOut: ActorRef, testMode: Boolean) extends FSM[State, Data] with StreamLogger {
  import BootAdd.Commands._
  import BootAdd.States._
  import BootAdd.StatesData._

  setLogSourceName(s"BootAdd*${self.path.name}")
  setLogKeys(Seq("BootAdd"))

  implicit val logQualifier = LogEntryQualifier("static")

  /** Original sender (Head Boot actor) */
  var orig = ActorRef.noSender

  startWith(Idle, Uninitialized)

  /** Staring mode. In this mode actor send request to the boot repository for create new entry*/
  when(Idle, 5 second) {
    case Event(Exec, _) =>
      orig = sender()

      val options = getOptions(nextArgs)
      val cOpt = options.get("-c")

      if (cOpt.isDefined) {
        env.bootRep ! BootRep.Commands.Create(cOpt.get)
        goto(WaitRepositoryResult) using InWaitRepositoryResult
      } else {
        orig ! Boot.Internals.Complete(20, s"Required -c arg")
        stop
      }


    //NOT TESTABLE
    case Event(StateTimeout, _) =>
      implicit val logQualifier = LogEntryQualifier("Idle_StateTimeout")
      logger.warning("FSM doest not run with expected timeout")
      stop
  }

  /** Waiting response from the boot repository */
  when(WaitRepositoryResult,  if (testMode) 0.5 second else 5 second) {

    /** Repository return success result. Command terminates with created definition id */
    case Event(defi: BootRep.Responses.BootDefinition, _) =>
      orig ! Boot.Internals.Complete(0, defi.id.toString)
      stop

    /** Repository return io error result. Command terminates with error text */
    case Event(BootRep.Responses.WriteError, _) =>
      orig ! Boot.Internals.Complete(21, "Configuration io error")
      stop


    /** Repository does not respond with expected timeout */
    case Event(StateTimeout, _) =>
      orig ! Boot.Internals.Complete(22, "Repository response timeout")
      stop
  }

  initialize()
}