package ru.serbis.okto.node.syscoms.storage

import akka.actor.{ActorRef, FSM, Props}
import akka.util.ByteString
import ru.serbis.okto.node.common.Env
import ru.serbis.okto.node.common.FsmDefaults.{Data, State}
import ru.serbis.okto.node.log.Logger.LogEntryQualifier
import ru.serbis.okto.node.log.StreamLogger
import ru.serbis.okto.node.reps.StorageRep
import ru.serbis.okto.node.runtime.{Stream, StreamControls}
import ru.serbis.okto.node.common.ReachTypes.ReachByteString

import scala.concurrent.duration._

object StorageWrite {

  /** @param nextArgs mode arguments
    * @param env node env object
    * @param stdIn command standard input
    * @param stdOut command standard output
    * @param testMode test mode
    */
  def props(nextArgs: Vector[String], env: Env, stdIn: ActorRef, stdOut: ActorRef, testMode: Boolean = false) =
    Props(new StorageWrite(nextArgs, env, stdIn, stdOut, testMode))

  object States {
    /** see description of the state code */
    case object Idle extends State
    /** see description of the state code */
    case object CollectFragment extends State
    /** see description of the state code */
    case object WaitResult extends State
  }

  object StatesData {
    /** n/c */
    case object Uninitialized extends Data
    /** n/c */
    case class InCollectFragment(file: String, start: Int, len: Int, inBuf: ByteString) extends Data
    /** n/c */
    case object InWaitResult extends Data
  }

  object Commands {

    /** Start fsm work */
    case object Exec
  }
}

class StorageWrite(nextArgs: Vector[String], env: Env, stdInt: ActorRef, stdOut: ActorRef, testMode: Boolean) extends FSM[State, Data] with StreamLogger {
  import StorageWrite.Commands._
  import StorageWrite.States._
  import StorageWrite.StatesData._

  setLogSourceName(s"StorageWrite*${self.path.name}")
  setLogKeys(Seq("StorageWrite"))

  implicit val logQualifier = LogEntryQualifier("static")

  /** Original sender (Head Storage actor) */
  var orig = ActorRef.noSender

  startWith(Idle, Uninitialized)

  /** Staring mode. In this mode actor send request to the storage repository for get file fragment */
  when(Idle, 5 second) {
    case Event(Exec, _) =>
      orig = sender()

      if (nextArgs.size < 3) {
        orig ! Storage.Internals.Complete(40, s"Required 3 args, but found ${nextArgs.size}")
        stop
      } else {
        try {
          val start = nextArgs(1).toInt
          val len = nextArgs(2).toInt
          stdOut ! Stream.Commands.WriteWrapped(ByteString().prompt)
          goto(CollectFragment) using InCollectFragment(nextArgs.head, start, len, ByteString.empty)
        } catch {
          case _: Throwable =>
            orig ! Storage.Internals.Complete(41, "Args 2 and 3 must be a number")
            stop
        }
      }


    //NOT TESTABLE
    case Event(StateTimeout, _) =>
      implicit val logQualifier = LogEntryQualifier("Idle_StateTimeout")
      logger.warning("FSM doest not run with expected timeout")
      stop
  }

  /** Expecting fragment from user */
  when(CollectFragment, if (testMode) 0.5 second else 60 second) {

    /** Data block from stdIn. If it is contain eoi char, it transform received data to the utf8 string, and
      * sent request to the storage repository to write fragment. If data block does not contain eoi, this data
      * was buffered and new data message expected. */
    case Event(Stream.Responses.Data(bs), data: InCollectFragment) =>
      val eoiIndex = bs.indexOf(StreamControls.EOI)
      if (eoiIndex == -1) {
        stay using data.copy(inBuf = data.inBuf ++ bs)
      } else {
        //TODO [5] ну а если за EOI блок данных, скрипт будет с хренью
        val frag = (data.inBuf ++ bs.slice(0, bs.size - 1)).utf8String
        //if (frag.isEmpty) {
        //  orig ! Storage.Internals.Complete(42, "Presented fragment is empty string")
        //  stop
        //} else {
          env.storageRep ! StorageRep.Commands.WriteFragment(data.file, data.start, data.len, ByteString(frag))
          goto(WaitResult) using InWaitResult
        //}
      }

    /** Script code does not presented with specified timeout */
    case Event(StateTimeout, _) =>
      orig ! Storage.Internals.Complete(43, "Fragment does not presented within 60 seconds")
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
      orig ! Storage.Internals.Complete(45, s"Unable to write file: $e")
      stop

    /** Repository does not respond with expected timeout */
    case Event(StateTimeout, _) =>
      orig ! Storage.Internals.Complete(44, "Internal error type 0")
      stop
  }

  initialize()
}