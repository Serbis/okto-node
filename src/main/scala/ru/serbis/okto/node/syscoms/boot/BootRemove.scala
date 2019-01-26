package ru.serbis.okto.node.syscoms.boot

import akka.actor.{ActorRef, FSM, Props}
import ru.serbis.okto.node.common.Env
import ru.serbis.okto.node.common.FsmDefaults.{Data, State}
import ru.serbis.okto.node.common.NodeUtils.getOptions
import ru.serbis.okto.node.log.Logger.LogEntryQualifier
import ru.serbis.okto.node.log.StreamLogger
import ru.serbis.okto.node.reps.{BootRep, StorageRep}

import scala.concurrent.duration._

object BootRemove {

  /** @param nextArgs mode arguments
    * @param env node env object
    * @param stdIn command standard input
    * @param stdOut command standard output
    * @param testMode test mode
    */
  def props(nextArgs: Vector[String], env: Env, stdIn: ActorRef, stdOut: ActorRef, testMode: Boolean = false) =
    Props(new BootRemove(nextArgs, env, stdIn, stdOut, testMode))

  object States {
    /** see description of the state code */
    case object Idle extends State
    /** see description of the state code */
    case object WaitRepositoryResult extends State
  }

  object StatesData {
    /** n/c */
    case object Uninitialized extends Data

    /** @param id removed definition id */
    case class InWaitRepositoryResult(id: Int) extends Data
  }

  object Commands {

    /** Start fsm work */
    case object Exec
  }
}

class BootRemove(nextArgs: Vector[String], env: Env, stdInt: ActorRef, stdOut: ActorRef, testMode: Boolean) extends FSM[State, Data] with StreamLogger {
  import BootRemove.Commands._
  import BootRemove.States._
  import BootRemove.StatesData._

  setLogSourceName(s"BootRemove*${self.path.name}")
  setLogKeys(Seq("BootRemove"))

  implicit val logQualifier = LogEntryQualifier("static")

  /** Original sender (Head Boot actor) */
  var orig = ActorRef.noSender

  startWith(Idle, Uninitialized)

  /** Staring mode. In this mode actor send request to the boot repository for remove definition */
  when(Idle, 5 second) {
    case Event(Exec, _) =>
      orig = sender()

      if (nextArgs.isEmpty) {
        orig ! Boot.Internals.Complete(30, s"Required boot entry id")
        stop
      } else {
        try {
          val did = nextArgs.head.toInt
          env.bootRep ! BootRep.Commands.Remove(did)
          goto(WaitRepositoryResult) using InWaitRepositoryResult(did)
        } catch {
          case _: Exception =>
            orig ! Boot.Internals.Complete(31, s"Boot entry id must be an integer")
            stop
        }
      }

    //NOT TESTABLE
    case Event(StateTimeout, _) =>
      implicit val logQualifier = LogEntryQualifier("Idle_StateTimeout")
      logger.warning("FSM doest not run with expected timeout")
      stop
  }

  /** Waiting response from the boot repository */
  when(WaitRepositoryResult,  if (testMode) 0.5 second else 5 second) {

    /** Repository return success result. Command terminates with OK text */
    case Event(BootRep.Responses.Removed, _) =>
      orig ! Boot.Internals.Complete(0, "OK")
      stop

    /** Repository return not exist result. Command terminates with error text */
    case Event(BootRep.Responses.NotExist, InWaitRepositoryResult(id)) =>
      orig ! Boot.Internals.Complete(34, s"Boot entry '$id' does not exist")
      stop

    /** Repository return io error result. Command terminates with error text */
    case Event(BootRep.Responses.WriteError, _) =>
      orig ! Boot.Internals.Complete(32, "Configuration io error")
      stop


    /** Repository does not respond with expected timeout */
    case Event(StateTimeout, _) =>
      orig ! Boot.Internals.Complete(33, "Repository response timeout")
      stop
  }

  initialize()
}