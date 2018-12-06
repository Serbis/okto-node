package ru.serbis.okto.node.syscoms.storage

import akka.actor.{ActorRef, FSM, Props}
import ru.serbis.okto.node.common.Env
import ru.serbis.okto.node.common.FsmDefaults.{Data, State}
import ru.serbis.okto.node.log.Logger.LogEntryQualifier
import ru.serbis.okto.node.log.StreamLogger
import ru.serbis.okto.node.reps.{ScriptsRep, StorageRep}

import scala.concurrent.duration._

object StorageInfo {

  /** @param nextArgs mode arguments
    * @param env node env object
    * @param stdIn command standard input
    * @param stdOut command standard output
    * @param testMode test mode
    */
  def props(nextArgs: Vector[String], env: Env, stdIn: ActorRef, stdOut: ActorRef, testMode: Boolean = false) =
    Props(new StorageInfo(nextArgs, env, stdIn, stdOut, testMode))

  object States {
    /** see description of the state code */
    case object Idle extends State
    /** see description of the state code */
    case object WaitFilesInfo extends State
  }

  object StatesData {
    /** n/c */
    case object Uninitialized extends Data
    /** n/c */
    case object InWaitFilesInfo extends Data
  }

  object Commands {

    /** Start fsm work */
    case object Exec
  }
}

class StorageInfo(nextArgs: Vector[String], env: Env, stdInt: ActorRef, stdOut: ActorRef, testMode: Boolean) extends FSM[State, Data] with StreamLogger {
  import StorageInfo.Commands._
  import StorageInfo.StatesData._
  import StorageInfo.States._

  setLogSourceName(s"StorageInfo*${self.path.name}")
  setLogKeys(Seq("StorageInfo"))

  implicit val logQualifier = LogEntryQualifier("static")

  /** Original sender (Head Storage actor) */
  var orig = ActorRef.noSender

  startWith(Idle, Uninitialized)

  /** Staring mode. In this mode actor send request to get files info from the storage repository */
  when(Idle, 5 second) {
    case Event(Exec, _) =>
      orig = sender()

      env.storageRep ! StorageRep.Commands.GetInfo(nextArgs.toList)
      goto(WaitFilesInfo) using InWaitFilesInfo

    //NOT TESTABLE
    case Event(StateTimeout, _) =>
      implicit val logQualifier = LogEntryQualifier("Idle_StateTimeout")
      logger.warning("FSM doest not run with expected timeout")
      stop
  }

  /** Waiting response from the storage repository */
  when(WaitFilesInfo,  if (testMode) 0.5 second else 5 second) {

    /** Repository return files info list. Command terminates with success result and print json printed result */
    case Event(StorageRep.Responses.FilesInfo(info), _) =>
      val p = "\""
      val json = s"{\n${
        info.foldLeft("") {(a, m) =>
          if (m._2.isRight) {
            val i = m._2.right.get
            s"$a\t$p${m._1}$p : {\n\t\t${p}size$p : ${i.size},\n\t\t${p}created$p : ${i.created},\n\t\t${p}modified$p : ${i.modified}\n\t},\n"
          } else {
            s"$a\t$p${m._1}$p : {\n\t\t${p}error$p : $p${m._2.left.get.getClass.getName}: ${m._2.left.get.getMessage}$p\n\t},\n"
          }
        }.dropRight(2) + "\n"
      }}"


      orig ! Storage.Internals.Complete(0, json)
      stop

    /** Repository respond with error */
    case Event(StorageRep.Responses.OperationError(e), _) =>
      orig ! Storage.Internals.Complete(12, s"Unable to read storage: $e")
      stop

    /** Repository does not respond with expected timeout */
    case Event(StateTimeout, _) =>
      orig ! Storage.Internals.Complete(11, "Internal error type 0")
      stop
  }

  initialize()
}