package ru.serbis.okto.node.syscoms.access

import akka.actor.{ActorRef, FSM, Props}
import ru.serbis.okto.node.access.AccessRep
import ru.serbis.okto.node.common.Env
import ru.serbis.okto.node.common.FsmDefaults.{Data, State}
import ru.serbis.okto.node.log.Logger.LogEntryQualifier
import ru.serbis.okto.node.log.StreamLogger
import ru.serbis.okto.node.common.NodeUtils.getEqualOptions

import scala.concurrent.duration._

object GroupAdd {

  /** @param nextArgs mode arguments
    * @param env node env object
    * @param stdIn command standard input
    * @param stdOut command standard output
    * @param testMode test mode
    */
  def props(nextArgs: Vector[String], env: Env, stdIn: ActorRef, stdOut: ActorRef, testMode: Boolean = false) =
    Props(new GroupAdd(nextArgs, env, stdIn, stdOut, testMode))

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

class GroupAdd(nextArgs: Vector[String], env: Env, stdInt: ActorRef, stdOut: ActorRef, testMode: Boolean) extends FSM[State, Data] with StreamLogger {
  import GroupAdd.Commands._
  import GroupAdd.States._
  import GroupAdd.StatesData._

  setLogSourceName(s"GroupAdd*${self.path.name}")
  setLogKeys(Seq("GroupAdd"))

  implicit val logQualifier = LogEntryQualifier("static")

  /** Original sender (Head Boot actor) */
  var orig = ActorRef.noSender

  startWith(Idle, Uninitialized)

  /** Staring mode. In this mode actor send request to the boot repository for create new entry*/
  when(Idle, 5 second) {
    case Event(Exec, _) =>
      orig = sender()

      val opts = getEqualOptions(nextArgs)
      if (opts.contains("name")) {
        val name = opts("name")

        val Pattern = "([A-Za-z0-9]+)".r
        val rCont = name match {
          case Pattern(_) => true
          case _ => false
        }

        if (rCont) {
          val permissions = if (opts.contains("permissions")) {
            opts("permissions").split(",").toVector
          } else {
            Vector.empty
          }

          env.accessRep ! AccessRep.Commands.AddGroup(name, permissions)

          goto(WaitRepositoryResult) using InWaitRepositoryResult
        } else {
          orig ! Access.Internals.Complete(26, "Name contains restricted symbols")
          stop
        }
      } else {
        orig ! Access.Internals.Complete(20, "Required mandatory args")
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
      orig ! Access.Internals.Complete(0, "OK")
      stop

    /** Group already exist in the system */
    case Event(AccessRep.Responses.Exist, _) =>
      orig ! Access.Internals.Complete(21, "Group already exists")
      stop

    /** Group has unknown permission name */
    case Event(AccessRep.Responses.UnknownPermission(perm), _) =>
      orig ! Access.Internals.Complete(23, s"Permission '$perm' is unknown")
      stop

    /** Configuration io error */
    case Event(AccessRep.Responses.WriteError(_), _) =>
      orig ! Access.Internals.Complete(24, "Configuration io error")
      stop

    /** Repository does not respond with expected timeout */
    case Event(StateTimeout, _) =>
      orig ! Access.Internals.Complete(25, "Repository response timeout")
      stop
  }

  initialize()
}