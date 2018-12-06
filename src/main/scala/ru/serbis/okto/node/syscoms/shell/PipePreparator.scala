package ru.serbis.okto.node.syscoms.shell

import akka.actor.{ActorRef, FSM, Props}
import ru.serbis.okto.node.common.Env
import ru.serbis.okto.node.common.FsmDefaults.{Data, State}
import ru.serbis.okto.node.common.ReachTypes.{ReachList, ReachSet}
import ru.serbis.okto.node.log.Logger.LogEntryQualifier
import ru.serbis.okto.node.log.StreamLogger
import ru.serbis.okto.node.reps.{SyscomsRep, UsercomsRep}
import ru.serbis.okto.node.runtime.{ProcessConstructor, Runtime}
import ru.serbis.okto.node.syscoms.shell.StatementsParser.CommandNode
import ru.serbis.okto.node.common.ReachTypes.ReachVector
import ru.serbis.okto.node.runtime.Process

import scala.annotation.tailrec
import scala.concurrent.duration._

/** This actor realizes the construction of a pipe. Pipe is a group of processes with consecutively closed input-output
  * streams. The standard output of the foreground process closes to the standard input of the subsequent flow. The
  * standard input of the first process and the standard output of the last are the endpoints of the pipe.
  */
object PipePreparator {

  /** @param env node's env object
    * @param testMode test mode flag
    */
  def props(env: Env, testMode: Boolean = false) =
    Props(new PipePreparator(env, testMode))

  object States {
    /** see description of the state code */
    case object Idle extends State
    /** see description of the state code */
    case object CollectRepsResponses extends State
    /** see description of the state code */
    case object HandleRepsResults extends State
    /** see description of the state code */
    case object ProcessesSpawning extends State
  }

  object StatesData {
    /** n/c */
    case object Uninitialized extends Data
    /** n/c */
    case class InCollectRepsResponses(sys: Option[SyscomsRep.Responses.CommandsBatch], usr: Option[UsercomsRep.Responses.CommandsBatch]) extends Data
    /** n/c */
    case class InHandleRepsResults(sys: SyscomsRep.Responses.CommandsBatch, usr: UsercomsRep.Responses.CommandsBatch) extends Data
    /** n/c */
    case class InProcessesSpawning(cmdName: String, args: Vector[String]) extends Data
  }

  object Commands {

    /** Start fsm to work
      *
      * @param commands list of nodes of commands from the expression parser ast
      */
    case class Exec(commands: List[CommandNode])
  }

  object Responses {

    /** The result of the successful execution of the procedure for constructing a pipe
      *
      * @param stdIn standard input of the first command process
      * @param stdOut standard output of the last command process
      */
    case class PipeCircuit(stdIn: ActorRef, stdOut: ActorRef)

    /** The answer is returned if some commands in the pipe do not exist on the node
      *
      * @param cmd set of nonexistent commands
      */
    case class CommandsNotFound(cmd: Set[String])

    /** The answer returned in the event of an unexpected program error in the fsm logic */
    case object InternalError
  }

  object Internals {

    /** Running the process of processing responses from repositories */
    case object ProcessResp
  }
}

class PipePreparator(env: Env, testMode: Boolean) extends FSM[State, Data] with StreamLogger {
  import PipePreparator.Commands._
  import PipePreparator.Internals._
  import PipePreparator.Responses._
  import PipePreparator.States._
  import PipePreparator.StatesData._

  setLogSourceName(s"PipePreparator*${self.path.name}")
  setLogKeys(Seq("PipePreparator"))

  implicit val logQualifier = LogEntryQualifier("static")

  /** Original sender */
  var orig = ActorRef.noSender

  /** Input commands list */
  var commands: List[CommandNode] = _

  startWith(Idle, Uninitialized)

  logger.debug("Pipe preparator initialed")

  /** Starting state. It sends requests to the repositories of system and user commands */
  when(Idle, 5 second) {
    case Event(Exec(c), _) =>
      implicit val logQualifier = LogEntryQualifier("Idle_Exec")
      orig = sender()
      commands = c

      val stc = commands.map(v => v.name)
      logger.debug(s"Executed request to configuration for '${stc.toSpacedString}'")
      env.syscomsRep ! SyscomsRep.Commands.GetCommandsBatch(stc)
      env.usercomsRep ! UsercomsRep.Commands.GetCommandsBatch(stc)
      goto(CollectRepsResponses) using InCollectRepsResponses(None, None)

    //NOT TESTABLE
    case Event(StateTimeout, _) =>
      implicit val logQualifier = LogEntryQualifier("Idle_StateTimeout")
      logger.warning("FSM doest not run with expected timeout")
      stop
  }

  /** Responses from repositories are received. When all the answers are received, the state of processing of
    * the results is transcended */
  when(CollectRepsResponses, if (testMode) 500 millis else 5 second) {
    case Event(r: UsercomsRep.Responses.CommandsBatch, data: InCollectRepsResponses) =>
      implicit val logQualifier = LogEntryQualifier("CollectRepsResponses_CommandsBatch")

      if (data.sys.isDefined) {
        self ! ProcessResp
        goto(HandleRepsResults) using InHandleRepsResults(data.sys.get, r)
      } else {
        stay using data.copy(usr = Some(r))
      }

    case Event(r: SyscomsRep.Responses.CommandsBatch, data: InCollectRepsResponses) =>
      implicit val logQualifier = LogEntryQualifier("CollectRepsResponses_CommandsBatch")

      if (data.usr.isDefined) {
        self ! ProcessResp
        goto(HandleRepsResults) using InHandleRepsResults(r, data.usr.get)
      } else {
        stay using data.copy(sys = Some(r))
      }

    case Event(StateTimeout, _) =>
      implicit val logQualifier = LogEntryQualifier("CollectRepsResponses_StateTimeout")
      logger.warning("The response from the repository was not received within the specified timeout")
      orig ! InternalError
      stop
  }

  /** It is verified that all the definitions of the requested commands have been received. If there is no definition,
    * the response is returned to CommanderNotFound with a list of commands not found. Otherwise, the spawning process
    * begins */
  when(HandleRepsResults, 5 second) {
    case Event(ProcessResp, data: InHandleRepsResults) =>
      implicit val logQualifier = LogEntryQualifier("ProcessRepsResults_Process")

      val notFound = collectNotFound(commands, data.sys, data.usr, Set.empty)
      if (notFound.isEmpty) {
        logger.debug("Collected all repositories result. Start processes spawning")
        val headCommand = commands.head
        val s = data.sys.commandsDef.get(headCommand.name)
        val u = data.usr.commandsDef.get(headCommand.name)
        val cmdDef = if (s.get.isDefined) s.get.get else u.get.get
        env.runtime ! Runtime.Commands.Spawn(headCommand.name, headCommand.args, cmdDef, self)
        goto(ProcessesSpawning) using InProcessesSpawning(headCommand.name, headCommand.args)
      } else {
        logger.debug(s"Can not create pipe, some commands not found '${notFound.toSpacedString}'")
        orig ! CommandsNotFound(notFound)
        stop
      }

    //NOT TESTABLE
    case Event(StateTimeout, _) =>
      implicit val logQualifier = LogEntryQualifier("ProcessRepsResults_StateTimeout")
      logger.warning("Strange state timeout")
      stop
  }

  /** Waits for a response from the runtime about results of the spawn process. If an error occurs, the InternalError
    * is sent to the originator. Otherwise, PipeCircuit closed to the created process is send to the originator */
  when(ProcessesSpawning, if (testMode) 500 millis else 5 second) {
    case Event(r: ProcessConstructor.Responses.ProcessDef, data: InProcessesSpawning) =>
      implicit val logQualifier = LogEntryQualifier("ProcessesSpawning_ProcessDef")
      logger.info(s"Process for command '${data.cmdName} ${data.args.toSpacedString}' was successfully spawned with pid '${r.pid}'")
      r.ref ! Process.Commands.Start
      orig ! PipeCircuit(r.streams(1), r.streams(0))
      stop

    case Event(Runtime.Responses.SpawnError, data: InProcessesSpawning) =>
      implicit val logQualifier = LogEntryQualifier("ProcessesSpawning_SpawnError")
      logger.warning(s"Process for command '$data' can not be created because an spawn error occurred")
      orig ! InternalError
      stop

    case Event(StateTimeout, _) =>
      implicit val logQualifier = LogEntryQualifier("ProcessesSpawning_StateTimeout")
      logger.warning("The response from the runtime was not received within the specified timeout")
      orig ! InternalError
      stop
  }

  initialize()


  @tailrec
  private def collectNotFound(commands: List[CommandNode], sys: SyscomsRep.Responses.CommandsBatch, usr: UsercomsRep.Responses.CommandsBatch, com: Set[String]): Set[String] = {
    if (commands.isEmpty) {
      com
    } else {
      val name = commands.head.name
      if (sys.commandsDef.getOrElse(name, None).isEmpty && usr.commandsDef.getOrElse(name, None).isEmpty)
        collectNotFound(commands.tailOrEmpty, sys, usr, com + name)
      else
        collectNotFound(commands.tailOrEmpty, sys, usr, com)
    }
  }
}
