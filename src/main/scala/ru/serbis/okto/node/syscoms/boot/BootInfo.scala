package ru.serbis.okto.node.syscoms.boot

import akka.actor.{ActorRef, FSM, Props}
import ru.serbis.okto.node.common.Env
import ru.serbis.okto.node.common.FsmDefaults.{Data, State}
import ru.serbis.okto.node.common.NodeUtils.getOptions
import ru.serbis.okto.node.log.Logger.LogEntryQualifier
import ru.serbis.okto.node.log.StreamLogger
import ru.serbis.okto.node.reps.{BootRep, StorageRep}

import scala.concurrent.duration._

object BootInfo {

  /** @param nextArgs mode arguments
    * @param env node env object
    * @param stdIn command standard input
    * @param stdOut command standard output
    * @param testMode test mode
    */
  def props(nextArgs: Vector[String], env: Env, stdIn: ActorRef, stdOut: ActorRef, testMode: Boolean = false) =
    Props(new BootInfo(nextArgs, env, stdIn, stdOut, testMode))

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

class BootInfo(nextArgs: Vector[String], env: Env, stdInt: ActorRef, stdOut: ActorRef, testMode: Boolean) extends FSM[State, Data] with StreamLogger {
  import BootInfo.Commands._
  import BootInfo.States._
  import BootInfo.StatesData._

  setLogSourceName(s"BootInfo*${self.path.name}")
  setLogKeys(Seq("BootInfo"))

  implicit val logQualifier = LogEntryQualifier("static")

  /** Original sender (Head Boot actor) */
  var orig = ActorRef.noSender

  startWith(Idle, Uninitialized)

  /** Staring mode. In this mode actor send request to get definitions from the boot repository */
  when(Idle, 5 second) {
    case Event(Exec, _) =>
      orig = sender()

      env.bootRep ! BootRep.Commands.GetAll

      goto(WaitRepositoryResult) using InWaitRepositoryResult

    //NOT TESTABLE
    case Event(StateTimeout, _) =>
      implicit val logQualifier = LogEntryQualifier("Idle_StateTimeout")
      logger.warning("FSM doest not run with expected timeout")
      stop
  }

  /** Waiting response from the boot repository */
  when(WaitRepositoryResult,  if (testMode) 0.5 second else 5 second) {

    /** Repository return definitions list. Command terminates with success result and print json printed result */
    case Event(defs: List[BootRep.Responses.BootDefinition], _) =>
      val p = "\""
      val json = s"[\n${
        defs.foldLeft("") {(a, m) =>
          s"$a\t{\n\t\t${p}id$p: ${m.id},\n\t\t${p}cmd$p: $p${m.cmd}$p\n\t},\n"
        }.dropRight(2) + "\n"
      }]"

      logger.debug(s"List of '${defs.size}' boot definitions was responded")
      orig ! Boot.Internals.Complete(0, json)
      stop

    /** Repository does not respond with expected timeout */
    case Event(StateTimeout, _) =>
      orig ! Boot.Internals.Complete(10, "Repository timeout")
      stop
  }

  initialize()
}