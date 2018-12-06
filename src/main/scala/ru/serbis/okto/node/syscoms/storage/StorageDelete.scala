package ru.serbis.okto.node.syscoms.storage

import akka.actor.{ActorRef, FSM, Props}
import ru.serbis.okto.node.common.Env
import ru.serbis.okto.node.common.FsmDefaults.{Data, State}
import ru.serbis.okto.node.log.Logger.LogEntryQualifier
import ru.serbis.okto.node.log.StreamLogger
import ru.serbis.okto.node.reps.StorageRep

import scala.concurrent.duration._

object StorageDelete {

  /** @param nextArgs mode arguments
    * @param env node env object
    * @param stdIn command standard input
    * @param stdOut command standard output
    * @param testMode test mode
    */
  def props(nextArgs: Vector[String], env: Env, stdIn: ActorRef, stdOut: ActorRef, testMode: Boolean = false) =
    Props(new StorageDelete(nextArgs, env, stdIn, stdOut, testMode))

  object States {
    /** see description of the state code */
    case object Idle extends State
    /** see description of the state code */
    case object WaitResult extends State
  }

  object StatesData {
    /** n/c */
    case object Uninitialized extends Data
    /** n/c */
    case object InWaitResult extends Data
  }

  object Commands {

    /** Start fsm work */
    case object Exec
  }
}

class StorageDelete(nextArgs: Vector[String], env: Env, stdInt: ActorRef, stdOut: ActorRef, testMode: Boolean) extends FSM[State, Data] with StreamLogger {
  import StorageDelete.Commands._
  import StorageDelete.States._
  import StorageDelete.StatesData._

  setLogSourceName(s"StorageDelete*${self.path.name}")
  setLogKeys(Seq("StorageDelete"))

  implicit val logQualifier = LogEntryQualifier("static")

  /** Original sender (Head Storage actor) */
  var orig = ActorRef.noSender

  startWith(Idle, Uninitialized)

  /** Staring mode. In this mode actor send request to the storage repository for get file fragment */
  when(Idle, 5 second) {
    case Event(Exec, _) =>
      orig = sender()

      if (nextArgs.isEmpty) {
        orig ! Storage.Internals.Complete(30, s"Required file name")
        stop
      } else {
        env.storageRep ! StorageRep.Commands.Delete(nextArgs.head)
        goto(WaitResult) using InWaitResult
      }


    //NOT TESTABLE
    case Event(StateTimeout, _) =>
      implicit val logQualifier = LogEntryQualifier("Idle_StateTimeout")
      logger.warning("FSM doest not run with expected timeout")
      stop
  }

  /** Waiting response from scripts repository */
  when(WaitResult,  if (testMode) 0.5 second else 5 second) {

    /** Repository return files info list. Command terminates with success result and print json printed result */
    case Event(StorageRep.Responses.OperationSuccess, _) =>
      orig ! Storage.Internals.Complete(0, "OK")
      stop

    /** Repository respond with error */
    case Event(StorageRep.Responses.OperationError(e), _) =>
      orig ! Storage.Internals.Complete(32, s"Unable to delete file: $e")
      stop

    /** Repository does not respond with expected timeout */
    case Event(StateTimeout, _) =>
      orig ! Storage.Internals.Complete(31, "Internal error type 0")
      stop
  }

  initialize()
}