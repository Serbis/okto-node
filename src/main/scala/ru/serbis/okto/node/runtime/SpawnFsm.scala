package ru.serbis.okto.node.runtime

import akka.actor.{ActorRef, FSM, Props}
import ru.serbis.okto.node.common.Env
import ru.serbis.okto.node.common.FsmDefaults.{Data, State}
import ru.serbis.okto.node.log.Logger.LogEntryQualifier
import ru.serbis.okto.node.log.StreamLogger
import ru.serbis.okto.node.reps.ScriptsRep
import ru.serbis.okto.node.reps.SyscomsRep.Responses.SystemCommandDefinition
import ru.serbis.okto.node.reps.UsercomsRep.Responses.UserCommandDefinition
import ru.serbis.okto.node.syscoms.{Echo, ReadAdc}
import ru.serbis.okto.node.syscoms.shell.Shell
import ru.serbis.okto.node.common.ReachTypes.ReachVector
import ru.serbis.okto.node.syscoms.pm.Pm
import ru.serbis.okto.node.syscoms.storage.Storage

import scala.concurrent.duration._

/** This actor is designed to create a new process for some command and run it */
object SpawnFsm {

  /** @param env node env object
    * @param testMode test mode flag
    */
  def props(env: Env, testMode: Boolean = false): Props =
    Props(new SpawnFsm(env, testMode))

  object States {
    /** see description of the state code */
    case object Idle extends State

    /** see description of the state code */
    case object WaitScriptCode extends State

    /** see description of the state code */
    case object WaitProcessCreation extends State

    /** see description of the state code */
    case object WaitInjecting extends State


  }

  object StatesData {
    /** n/c */
    case object Uninitialized extends Data

    case object WaitingProcessCreation extends Data

    case class WaitingScriptCode(cmd: String, args: Vector[String], initiator: ActorRef) extends Data

    /** @param processDef created process definition */
    case class WaitingInjecting(processDef: ProcessConstructor.Responses.ProcessDef) extends Data
  }

  object Commands {

    /** @param cmd running command
      * @param args command arguments
      * @param cmdDef command definition (if it is UserCommandDef, then it means running the application command on vm)
      * @param initiator initiator of command start (read shell sender, the one who sent ExecCmd to the shell actor)
      */
    case class Exec(cmd: String, args: Vector[String], cmdDef: Any, initiator: ActorRef)
  }
}

class SpawnFsm(env: Env, testMode: Boolean) extends FSM[State, Data] with StreamLogger {
  import SpawnFsm.Commands._
  import SpawnFsm.States._
  import SpawnFsm.StatesData._

  setLogSourceName(s"SpawnFsm*${self.path.name}")
  setLogKeys(Seq("SpawnFsm"))

  implicit val logQualifier = LogEntryQualifier("Static")

  var orig = ActorRef.noSender

  startWith(Idle, Uninitialized)

  /** Starting state. Creates an executor of the command based on its name and the sign of the system command.
    * Then he starts the procedure for creating a new process based on this executor */
  when(Idle, 5 second) {
    case Event(req: Exec, _) =>
      implicit val logQualifier = LogEntryQualifier("Idle_Exec")
      orig = sender()

      req.cmdDef match {
        case d: SystemCommandDefinition =>
          val executor = req.cmd match {
            case "echo" => Some(context.system.actorOf(Echo.props(env, req.args), s"Executor_${System.currentTimeMillis()}"))
            case "readAdc" => Some(context.system.actorOf(ReadAdc.props(env, req.args), s"Executor_${System.currentTimeMillis()}"))
            case "shell" => Some(context.system.actorOf(Shell.props(env, req.args), s"Executor_${System.currentTimeMillis()}"))
            case "pm" => Some(context.system.actorOf(Pm.props(env, req.args), s"Executor_${System.currentTimeMillis()}"))
            case "storage" => Some(context.system.actorOf(Storage.props(env, req.args), s"Executor_${System.currentTimeMillis()}"))
            case _ => None
          }

          if (executor.isDefined) {
            val processConstructor = context.system.actorOf(ProcessConstructor.props(env))
            processConstructor ! ProcessConstructor.Commands.Exec(executor.get, req.initiator)
            goto(WaitProcessCreation) using WaitingProcessCreation
          } else {
            logger.error(s"System command '${req.cmd}' does not found")
            sender() ! Runtime.Responses.SpawnError
            stop
          }

        case d: UserCommandDefinition =>
          env.scriptsRep ! ScriptsRep.Commands.GetScript(d.file)
          goto(WaitScriptCode) using WaitingScriptCode(req.cmd, req.args, req.initiator)

        case _ => stop
      }


    case Event(StateTimeout, _) =>
      implicit val logQualifier = LogEntryQualifier("Idle_StateTimeout")
      logger.warning("Exec command wait timeout")
      stop
  }

  when(WaitScriptCode, if (testMode) 0.5 second else 5 second) {
    case Event(ScriptsRep.Responses.Script(code), WaitingScriptCode(cmd, args, initiator)) =>
      implicit val logQualifier = LogEntryQualifier("WaitScriptCode_Script")
      val executor = context.system.actorOf(AppCmdExecutor.props(env, Vector(cmd, code) ++ args), s"Executor_${System.currentTimeMillis()}")
      val processConstructor = context.system.actorOf(ProcessConstructor.props(env))
      processConstructor ! ProcessConstructor.Commands.Exec(executor, initiator)
      goto(WaitProcessCreation) using WaitingProcessCreation

    case Event(ScriptsRep.Responses.ScriptNotFound, WaitingScriptCode(cmd, args, _)) =>
      implicit val logQualifier = LogEntryQualifier("WaitScriptCode_ScriptNotFound")
      logger.error(s"Script code for command '$cmd ${args.toSpacedString}' can not be obtained") //TODO [5]  увеиличить информативность логов в части какая команда / какой процесс и т д
      orig ! Runtime.Responses.SpawnError
      stop

    case Event(StateTimeout, WaitingScriptCode(cmd, args, _)) =>
      implicit val logQualifier = LogEntryQualifier("WaitScriptCode_StateTimeout")
      logger.warning(s"Scripts repository for command '$cmd ${args.toSpacedString}' does not respond with expected timeout")
      orig ! Runtime.Responses.SpawnError
      stop
  }

  /** Waits for the process to complete. If the result is successful, adds this process to the runtime with the
    * specified process ID. In the event of a process creation error, SpawnError returns */
  when(WaitProcessCreation, if (testMode) 1 second else 10 second) {
    case Event(req: ProcessConstructor.Responses.ProcessDef, WaitingProcessCreation) =>
      implicit val logQualifier = LogEntryQualifier("WaitProcessCreation_CreatedProcess")
      env.runtime ! Runtime.Commands.Inject(req.ref, req.pid)
      goto(WaitInjecting) using WaitingInjecting(req)

    case Event(ProcessConstructor.Responses.Error, WaitingProcessCreation) =>
      implicit val logQualifier = LogEntryQualifier("WaitProcessCreation_Error")
      log.error("Process constructor respond with error")
      orig ! Runtime.Responses.SpawnError
      stop

    case Event(StateTimeout, WaitingProcessCreation) =>  //Non-testable functional
      implicit val logQualifier = LogEntryQualifier("WaitProcessCreation_StateTimeout")
      logger.error("ProcessConstructor does not respond with expected timeout")
      orig ! Runtime.Responses.SpawnError
      stop
  }

  /** Waits for a response from the runtime process. After receiving a positive process, its identifier is sent to
    * the initiator and starts the process for execution. In case of an add error, returns a SpawnError */
  when(WaitInjecting, if (testMode) 1 second else 5 second) {
    case Event(Runtime.Responses.Injected, data: WaitingInjecting) =>
      implicit val logQualifier = LogEntryQualifier("WaitInjecting_Injected")
      orig ! data.processDef
      //data.processDef.ref ! Process.Commands.Start
      stop

    case Event(StateTimeout, data: WaitingInjecting) =>
      implicit val logQualifier = LogEntryQualifier("WaitInjecting_StateTimeout")
      logger.error("Runtime does not respond with expected timeout")
      orig ! Runtime.Responses.SpawnError
      stop
  }

  initialize()
}