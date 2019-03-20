package ru.serbis.okto.node.syscoms.access

import akka.actor.{ActorRef, FSM, Props}
import ru.serbis.okto.node.access.AccessRep
import ru.serbis.okto.node.common.Env
import ru.serbis.okto.node.common.FsmDefaults.{Data, State}
import ru.serbis.okto.node.common.NodeUtils.getEqualOptions
import ru.serbis.okto.node.log.Logger.LogEntryQualifier
import ru.serbis.okto.node.log.StreamLogger

import scala.concurrent.duration._

object GroupDel {

  /** @param nextArgs mode arguments
    * @param env node env object
    * @param stdIn command standard input
    * @param stdOut command standard output
    * @param testMode test mode
    */
  def props(nextArgs: Vector[String], env: Env, stdIn: ActorRef, stdOut: ActorRef, testMode: Boolean = false) =
    Props(new GroupDel(nextArgs, env, stdIn, stdOut, testMode))

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

class GroupDel(nextArgs: Vector[String], env: Env, stdInt: ActorRef, stdOut: ActorRef, testMode: Boolean) extends FSM[State, Data] with StreamLogger {
  import GroupDel.Commands._
  import GroupDel.States._
  import GroupDel.StatesData._

  setLogSourceName(s"GroupDel*${self.path.name}")
  setLogKeys(Seq("GroupDel"))

  implicit val logQualifier = LogEntryQualifier("static")

  /** Original sender (Head Access actor) */
  var orig = ActorRef.noSender

  startWith(Idle, Uninitialized)

  /** Staring mode. In this mode actor send request to the access repository for delete group */
  when(Idle, 5 second) {
    case Event(Exec, _) =>
      orig = sender()

      val opts = getEqualOptions(nextArgs)
      if (opts.contains("name")) {
        val name = opts("name")
        val recursive = opts.getOrElse("recursive", "false")
        val bRec = try {
          recursive.toBoolean
        } catch {
          case _: Throwable => false
        }

        env.accessRep ! AccessRep.Commands.DelGroup(name, recursive = bRec)

        goto(WaitRepositoryResult) using InWaitRepositoryResult
      } else {
        orig ! Access.Internals.Complete(40, "Required mandatory args")
        stop
      }


    //NOT TESTABLE
    case Event(StateTimeout, _) =>
      implicit val logQualifier = LogEntryQualifier("Idle_StateTimeout")
      logger.warning("FSM doest not run with expected timeout")
      stop
  }

  /** Waiting response from the access repository */
  when(WaitRepositoryResult,  if (testMode) 0.5 second else 5 second) {

    /** Repository return success result. Command terminates with OK result */
    case Event(AccessRep.Responses.Success, _) =>
      orig ! Access.Internals.Complete(0,"OK")
      stop

    /** Group does not exists in the system */
    case Event(AccessRep.Responses.NotExist, _) =>
      orig ! Access.Internals.Complete(41, "Group does not exists")
      stop

    /** Configuration io error */
    case Event(AccessRep.Responses.WriteError(_), _) =>
      orig ! Access.Internals.Complete(42, "Configuration io error")
      stop

    /** Repository does not respond with expected timeout */
    case Event(StateTimeout, _) =>
      orig ! Access.Internals.Complete(43, "Repository response timeout")
      stop
  }

  initialize()
}