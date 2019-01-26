package ru.serbis.okto.node.boot

import akka.actor.{ActorRef, FSM, Props}
import akka.util.ByteString
import ru.serbis.okto.node.common.{CommandsUnion, Env}
import ru.serbis.okto.node.common.FsmDefaults.{Data, State}
import ru.serbis.okto.node.log.Logger.LogEntryQualifier
import ru.serbis.okto.node.log.StreamLogger
import ru.serbis.okto.node.proxy.system.ActorSystemProxy
import ru.serbis.okto.node.reps.BootRep
import ru.serbis.okto.node.reps.BootRep.Responses.BootDefinition
import ru.serbis.okto.node.syscoms.shell.Shell
import ru.serbis.okto.node.runtime.{Stream, StreamControls}
import ru.serbis.okto.node.common.ReachTypes.ReachByteString
import ru.serbis.okto.node.common.ReachTypes.ReachList
import scala.concurrent.duration._

/** Boot manager - implements the startup programs immediately after starting the system. The principle of operation is
  * based on loading definitions from the boot repository, after which the sequential launch of the commands
  * registered there through the shell. Since the program being started can capture I / O streams, the shell does not
  * close and ends up on its own timeout in normal conditions.
  *
  * Animation! The program being launched should under no circumstances request input, as this may disrupt the launch process
  * of other programs.
  */
object BootManager {

  /** @param env node env object
    * @param testMode test mode flag
    */
  def props(env: Env, systemEx: ActorSystemProxy, testMode: Boolean = false): Props =
    Props(new BootManager(env, systemEx, testMode))

  object States {
    /** see description of the state code */
    case object Idle extends State

    /** see description of the state code */
    case object WaitBootList extends State

    /** see description of the state code */
    case object SpawnCommands extends State


  }

  object StatesData {
    /** n/c */
    case object Uninitialized extends Data

    /** n/c */
    case object InWaitBootList extends Data

    /** @param cmds unprocessed boot definitions
      * @param shell current shell command actor
      */
    case class InSpawnCommands(shell: ActorRef, cmds: List[BootDefinition]) extends Data
  }

  object Commands {

    /** n/c */
    case class Exec()
  }
}
class BootManager(env: Env, systemEx: ActorSystemProxy, testMode: Boolean) extends FSM[State, Data] with StreamLogger {
  import BootManager.Commands._
  import BootManager.States._
  import BootManager.StatesData._

  setLogSourceName(s"BootManager*${self.path.name}")
  setLogKeys(Seq("BootManager"))

  implicit val logQualifier = LogEntryQualifier("Static")

  var orig = ActorRef.noSender

  startWith(Idle, Uninitialized)

  /** Request boot definitions from the repository */
  when(Idle, 5 second) {

    case Event(_: Exec, _) =>
      implicit val logQualifier = LogEntryQualifier("Idle_Exec")
      orig = sender()

      logger.info("Boot manager was started")

      env.bootRep ! BootRep.Commands.GetAll

      goto(WaitBootList) using InWaitBootList


    // NOT TESTABLE
    case Event(StateTimeout, _) =>
      implicit val logQualifier = LogEntryQualifier("Idle_StateTimeout")
      logger.warning("Exec command wait timeout")
      stop
  }

  /** Recive boot definitions from the repository. If nothing to start, complete the work, otherwise create first shell
    * and go to iterative command spawning */
  when(WaitBootList, 5 second) {

    case Event(list: List[BootRep.Responses.BootDefinition], _) =>
      implicit val logQualifier = LogEntryQualifier("WaitBootList_BootDefinitions")

      if (list.isEmpty) {
        logger.info("No commands for boot")
        stop
      } else {
        val shell = systemEx.actorOf(Shell.props(env, Vector("boot")), Some("BootShell_0"))
        shell ! CommandsUnion.Commands.Run(context.system.deadLetters, Map(0 -> self, 1 -> self))

        goto(SpawnCommands) using InSpawnCommands(shell, list)
      }

    // NOT TESTABLE
    case Event(StateTimeout, _) =>
      implicit val logQualifier = LogEntryQualifier("WaitBootList_StateTimeout")
      logger.warning("Boot repository response timeout")
      stop
  }

  /** Realize iterative commands spawning. For each command it's create new shell, wair for prompt for it, and then send
    * command for execution */
  when(SpawnCommands, 5 second) {

    case Event(Stream.Commands.WriteWrapped(bs), d @ InSpawnCommands(shell, cmds)) =>
      implicit val logQualifier = LogEntryQualifier("SpawnCommands_Data")


      if (bs.head == StreamControls.PROMPT) {
        // If not more command for boot close last shell and it's streams
        if (cmds.isEmpty) {
          stop
        } else {
          val cmd = cmds.head.cmd
          logger.info(s"Boot command '$cmd'")
          shell ! Stream.Responses.Data(ByteString(cmd).eoi)
          val nc = cmds.tailOrEmpty
          if (nc.nonEmpty) {
            val nShell = systemEx.actorOf(Shell.props(env, Vector("boot")), Some(s"BootShell_${cmds.size}"))
            nShell ! CommandsUnion.Commands.Run(context.system.deadLetters, Map(0 -> self, 1 -> self))
            stay using d.copy(shell = nShell, cmds = nc)
          } else
            stay using d.copy(shell = ActorRef.noSender, cmds = nc)

        }
      } else {
        stop
      }

    // NOT TESTABLE
    case Event(StateTimeout, _) =>
      implicit val logQualifier = LogEntryQualifier("SpawnCommands_StateTimeout")
      logger.warning("Shell prompt timeout timeout")
      stop
  }

  initialize()
}