package ru.serbis.okto.node.syscoms.storage

import akka.actor.{ActorRef, FSM, Props}
import ru.serbis.okto.node.common.Env
import ru.serbis.okto.node.common.FsmDefaults.{Data, State}
import ru.serbis.okto.node.log.Logger.LogEntryQualifier
import ru.serbis.okto.node.log.StreamLogger
import ru.serbis.okto.node.reps.StorageRep

import scala.concurrent.duration._

object StorageRead {

  /** @param nextArgs mode arguments
    * @param env node env object
    * @param stdIn command standard input
    * @param stdOut command standard output
    * @param testMode test mode
    */
  def props(nextArgs: Vector[String], env: Env, stdIn: ActorRef, stdOut: ActorRef, testMode: Boolean = false) =
    Props(new StorageRead(nextArgs, env, stdIn, stdOut, testMode))

  object States {
    /** see description of the state code */
    case object Idle extends State
    /** see description of the state code */
    case object WaitFragment extends State
  }

  object StatesData {
    /** n/c */
    case object Uninitialized extends Data
    /** n/c */
    case object InWaitFragment extends Data
  }

  object Commands {

    /** Start fsm work */
    case object Exec
  }
}

class StorageRead(nextArgs: Vector[String], env: Env, stdInt: ActorRef, stdOut: ActorRef, testMode: Boolean) extends FSM[State, Data] with StreamLogger {
  import StorageRead.Commands._
  import StorageRead.States._
  import StorageRead.StatesData._

  setLogSourceName(s"StorageRead*${self.path.name}")
  setLogKeys(Seq("StorageRead"))

  implicit val logQualifier = LogEntryQualifier("static")

  /** Original sender (Head Storage actor) */
  var orig = ActorRef.noSender

  startWith(Idle, Uninitialized)

  /** Staring mode. In this mode actor send request to the storage repository for get file fragment */
  when(Idle, 5 second) {
    case Event(Exec, _) =>
      orig = sender()

      if (nextArgs.size < 3) {
        orig ! Storage.Internals.Complete(20, s"Required 3 args, but found ${nextArgs.size}")
        stop
      } else {
        try {
          val start = nextArgs(1).toInt
          val len = nextArgs(2).toInt
          env.storageRep ! StorageRep.Commands.ReadFragment(nextArgs.head, start, len)
          goto(WaitFragment) using InWaitFragment
        } catch {
          case _: Throwable =>
            orig ! Storage.Internals.Complete(21, "Args 2 and 3 must be a number")
            stop
        }
      }


    //NOT TESTABLE
    case Event(StateTimeout, _) =>
      implicit val logQualifier = LogEntryQualifier("Idle_StateTimeout")
      logger.warning("FSM doest not run with expected timeout")
      stop
  }

  /** Waiting response from scripts repository */
  when(WaitFragment,  if (testMode) 0.5 second else 5 second) {

    /** Repository return files info list. Command terminates with success result and print json printed result */
    case Event(StorageRep.Responses.BlobData(data), _) =>
      orig ! Storage.Internals.Complete(0, data.utf8String)
      stop

    /** Repository respond with error */
    case Event(StorageRep.Responses.OperationError(e), _) =>
      orig ! Storage.Internals.Complete(23, s"Unable to read file: $e")
      stop

    /** Repository does not respond with expected timeout */
    case Event(StateTimeout, _) =>
      orig ! Storage.Internals.Complete(22, "Internal error type 0")
      stop
  }

  initialize()
}