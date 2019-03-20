package ru.serbis.okto.node.syscoms.access

import akka.actor.{ActorRef, FSM, Props}
import ru.serbis.okto.node.access.AccessRep
import ru.serbis.okto.node.common.Env
import ru.serbis.okto.node.common.FsmDefaults.{Data, State}
import ru.serbis.okto.node.log.Logger.LogEntryQualifier
import ru.serbis.okto.node.log.StreamLogger

import scala.concurrent.duration._

object UserList {

  /** @param nextArgs mode arguments
    * @param env node env object
    * @param stdIn command standard input
    * @param stdOut command standard output
    * @param testMode test mode
    */
  def props(nextArgs: Vector[String], env: Env, stdIn: ActorRef, stdOut: ActorRef, testMode: Boolean = false) =
    Props(new UserList(nextArgs, env, stdIn, stdOut, testMode))

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

class UserList(nextArgs: Vector[String], env: Env, stdInt: ActorRef, stdOut: ActorRef, testMode: Boolean) extends FSM[State, Data] with StreamLogger {
  import UserList.Commands._
  import UserList.States._
  import UserList.StatesData._

  setLogSourceName(s"UserList*${self.path.name}")
  setLogKeys(Seq("UserList"))

  implicit val logQualifier = LogEntryQualifier("static")

  /** Original sender (Head Boot actor) */
  var orig = ActorRef.noSender

  startWith(Idle, Uninitialized)

  /** Staring mode. In this mode actor send request to the boot repository for create new entry*/
  when(Idle, 5 second) {
    case Event(Exec, _) =>
      orig = sender()

      env.accessRep ! AccessRep.Commands.GetAccessConfig

      goto(WaitRepositoryResult) using InWaitRepositoryResult


    //NOT TESTABLE
    case Event(StateTimeout, _) =>
      implicit val logQualifier = LogEntryQualifier("Idle_StateTimeout")
      logger.warning("FSM doest not run with expected timeout")
      stop
  }

  /** Waiting response from the access repository */
  when(WaitRepositoryResult,  if (testMode) 0.5 second else 5 second) {

    /** Repository return success result. Command terminates with OK result */
    case Event(cfg: AccessRep.Definitions.AccessConfig, _) =>
      val c = "\""
      val body = cfg.users.foldLeft("")((a, v) => {
        val perms = v.permissions.foldLeft("")((a, v) => s"$a$c$v$c, ").dropRight(2)
        val groups = v.groups.foldLeft("")((a, v) => s"$a$c$v$c, ").dropRight(2)
        s"$a\t{\n\t\t${c}name$c: $c${v.name}$c,\n\t\t${c}password$c: $c***$c,\n\t\t${c}permissions$c: [$perms],\n\t\t${c}groups$c: [$groups]\n\t},\n"
      }).dropRight(2)

      orig ! Access.Internals.Complete(0, s"[\n$body\n]")

      stop


    /** Repository does not respond with expected timeout */
    case Event(StateTimeout, _) =>
      orig ! Access.Internals.Complete(71, "Repository response timeout")
      stop
  }

  initialize()
}