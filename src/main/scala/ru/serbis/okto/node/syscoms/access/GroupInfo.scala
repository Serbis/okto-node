package ru.serbis.okto.node.syscoms.access

import akka.actor.{ActorRef, FSM, Props}
import ru.serbis.okto.node.access.AccessRep
import ru.serbis.okto.node.common.Env
import ru.serbis.okto.node.common.FsmDefaults.{Data, State}
import ru.serbis.okto.node.common.NodeUtils.getEqualOptions
import ru.serbis.okto.node.log.Logger.LogEntryQualifier
import ru.serbis.okto.node.log.StreamLogger

import scala.concurrent.duration._

object GroupInfo {

  /** @param nextArgs mode arguments
    * @param env node env object
    * @param stdIn command standard input
    * @param stdOut command standard output
    * @param testMode test mode
    */
  def props(nextArgs: Vector[String], env: Env, stdIn: ActorRef, stdOut: ActorRef, testMode: Boolean = false) =
    Props(new GroupInfo(nextArgs, env, stdIn, stdOut, testMode))

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
    case class InWaitRepositoryResult(name: String) extends Data
  }

  object Commands {

    /** Start fsm work */
    case object Exec
  }
}

class GroupInfo(nextArgs: Vector[String], env: Env, stdInt: ActorRef, stdOut: ActorRef, testMode: Boolean) extends FSM[State, Data] with StreamLogger {
  import GroupInfo.Commands._
  import GroupInfo.States._
  import GroupInfo.StatesData._

  setLogSourceName(s"GroupInfo*${self.path.name}")
  setLogKeys(Seq("GroupInfo"))

  implicit val logQualifier = LogEntryQualifier("static")

  /** Original sender (Head Access actor) */
  var orig = ActorRef.noSender

  startWith(Idle, Uninitialized)

  /** Staring mode. In this mode actor send request to the access repository for get access definitions */
  when(Idle, 5 second) {
    case Event(Exec, _) =>
      orig = sender()

      val opts = getEqualOptions(nextArgs)
      if (opts.contains("name")) {
        val name = opts("name")

        env.accessRep ! AccessRep.Commands.GetAccessConfig

        goto(WaitRepositoryResult) using InWaitRepositoryResult(name)
      } else {
        orig ! Access.Internals.Complete(60, "Required mandatory args")
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
    case Event(cfg: AccessRep.Definitions.AccessConfig, InWaitRepositoryResult(name)) =>
      val fin = cfg.groups.find(v => v.name == name)
      if (fin.isDefined) {
        val c = "\""
        val perms = fin.get.permissions.foldLeft("")((a, v) => s"$a$c$v$c, ").dropRight(2)
        orig ! Access.Internals.Complete(0, s"{\n\t${c}name$c: $c${fin.get.name}$c,\n\t${c}permissions$c: [$perms]\n}")
      } else
        orig ! Access.Internals.Complete(61,"Group does not exist")
      stop


    /** Repository does not respond with expected timeout */
    case Event(StateTimeout, _) =>
      orig ! Access.Internals.Complete(62, "Repository response timeout")
      stop
  }

  initialize()
}