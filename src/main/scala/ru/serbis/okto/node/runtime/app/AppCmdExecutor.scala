package ru.serbis.okto.node.runtime.app

import akka.actor.{ActorRef, Props}
import akka.util.ByteString
import javax.script.{ScriptContext, ScriptEngine, ScriptException}
import ru.serbis.okto.node.common.FsmDefaults.{Data, State}
import ru.serbis.okto.node.common.ReachTypes.{ReachByteString, ReachVector}
import ru.serbis.okto.node.common.{CommandsUnion, Env}
import ru.serbis.okto.node.log.Logger.LogEntryQualifier
import ru.serbis.okto.node.log.StreamLogger
import ru.serbis.okto.node.reps.SyscomsRep.Responses.SystemCommandDefinition
import ru.serbis.okto.node.runtime.senv.ScriptEnvInstance
import ru.serbis.okto.node.runtime.{CmdExecutor, Process, ProcessConstructor, Runtime, Stream, VmPool}
import ru.serbis.okto.node.testut.{ActorSystemExpander, RealActorSystem}

import scala.concurrent.duration._

/** Executioner execution of a custom script. From a technical point of view, it is a subspecies of the system command.
  * In the arguments of the start, the following arguments are passed to it: script name, script code, script arguments.
  * The task is to create and run a script virtual machine and service requests coming from it to the external
  * environment.
  */
object AppCmdExecutor {
  /** n/c */
  def props(env: Env, args: Vector[String], systemEx: ActorSystemExpander = new RealActorSystem, testMode: Boolean = false) =
    Props(new AppCmdExecutor(env, args, systemEx, testMode))

  object States {
    /** see description of the state code */
    case object Idle extends State
    /** see description of the state code */
    case object WaitVmInstance extends State
    /** see description of the state code */
    case object ScriptExecution extends State
    /** see description of the state code */
    case object ShellSpawn extends State
    /** see description of the state code */
    case object CompleteExecution extends State

  }

  object StatesData {
    /** n/c */
    case object Uninitialized extends Data
    /** n/c */
    case class InWaitVmInstance() extends Data
    /** n/c */
    case class InScriptExecution(scriptEnf: ScriptEnvInstance, thread: Thread) extends Data
    /** n/c */
    case class InShellSpawn(sEnv: ActorRef, scriptEnv: ScriptEnvInstance, thread: Thread) extends Data
    /** n/c */
    case class InCompleteExecution() extends Data
  }

  object Commands {
    /** see ScriptExecution state description */
    case class StdOutWrite(data: ByteString)
    /** see ScriptExecution state description */
    case class Exit(code: Int)
    /** see ScriptExecution state description */
    case class CreateLocalShell()
  }

  object Internals {

    /** Self sent message in state CompleteExecution. For detail, see state description
      *
      * @param code exit code
      * @param message exit message
      */
    case class Complete(code: Int, message: String)
  }

  object Responses {
    case class ShellDefinition(process: ActorRef, stdIn: ActorRef, stdOut: ActorRef)
    case object ShellCreationError
  }
}

class AppCmdExecutor(env: Env, args: Vector[String], systemEx: ActorSystemExpander, testMode: Boolean) extends CmdExecutor(systemEx, testMode) {
  import AppCmdExecutor.Commands._
  import AppCmdExecutor.Internals._
  import AppCmdExecutor.Responses._
  import AppCmdExecutor.States._
  import AppCmdExecutor.StatesData._


  setLogSourceName(s"AppCmdExecutor*${self.path.name}")
  setLogKeys(Seq("AppCmdExecutor"))

  var process = ActorRef.noSender
  var streams = Map.empty[Int, ActorRef]
  var vmRuntime: Option[ScriptEngine] = None

  startWith(Idle, Uninitialized)

  logger.debug("App command initialization logic was started")

  /** Starting state. The executor work begins with a request to the pool of virtual machines to receive a new runtime
    * copy */
  when(Idle, if (testMode) 0.5 second else 5 second) {
    case Event(req: CommandsUnion.Commands.Run, _) =>
      implicit val logQualifier = LogEntryQualifier("Idle_Run")
      process = req.process
      streams = req.streams

      env.vmPool ! VmPool.Commands.Reserve
      goto(WaitVmInstance) using InWaitVmInstance()

    //NOT TESTABLE
    case Event(StateTimeout, _) =>
      implicit val logQualifier = LogEntryQualifier("Idle_StateTimeout")
      logger.warning("FSM doest not run with expected timeout")
      stop
  }

  /** A response is expected from the virtual machine pool */
  when(WaitVmInstance, if (testMode) 0.5 second else 5 second) {

    /** The response from the pool containing the instance of the virtual machine. The handler creates an environment
      * for running the script and runs a new script execution thread with it.*/
    case Event(VmPool.Responses.VmInstance(vm), _) =>
      implicit val logQualifier = LogEntryQualifier("WaitVmInstance_VmInstance")
      val scriptEnv = new ScriptEnvInstance(self, streams(1), streams(0), env)
      vmRuntime = Some(vm)
      val thread = new Thread(new ScriptThread(scriptEnv, vm, args(1), args.slice(2, args.size)))
      thread.start()
      logger.debug("Script runtime was started")
      goto(ScriptExecution) using InScriptExecution(scriptEnv, thread)

    /** Answer from the pool with an error about overflow. The handler terminates the execution of the executor with
      * the appropriate error code. */
    case Event(VmPool.Responses.PoolOverflow, _) =>
      implicit val logQualifier = LogEntryQualifier("WaitVmInstance_PoolOverflow")
      logger.warning("Unable to reserve vm runtime")
      self ! Complete(1, "Unable to reserve vm runtime")
      goto(CompleteExecution) using InCompleteExecution()

    case Event(StateTimeout, _) =>
      implicit val logQualifier = LogEntryQualifier("WaitVmInstance_StateTimeout")
      logger.warning("VmPool does not respond with expected timeout")
      self ! Complete(2, "Internal error 1")
      goto(CompleteExecution) using InCompleteExecution()
  }

  /** In this state, the work of the executor is processed in the script running mode. The transition to the state
    * occurs immediately after the start of the script thread. The exit from the state is the completion of the script.
    * Messages can come from the side of the node execution system or from the script's environment. */
  when(ScriptExecution) {

    /** Writes the data to the standard output of the program. This message comes from the script environment, if
      * script need to perform an output operation on the standard output stream */
    case Event(StdOutWrite(data), InScriptExecution(scriptEnv, thread)) =>
      implicit val logQualifier = LogEntryQualifier("ScriptExecution_StdOutWrite")
      logger.debug(s"Write data to StdOut from script '${data.toHexString}'")
      streams(0).tell(Stream.Commands.WriteWrapped(data), ActorRef.noSender)
      stay

    /** Exits the program with the specified exit code. The message comes from the script's environment, when the user
      * code is explicitly or implicitly terminated. */
    case Event(Exit(code), _) =>
      implicit val logQualifier = LogEntryQualifier("ScriptExecution_StdOutWrite")
      if (vmRuntime.isDefined)
        env.vmPool ! VmPool.Commands.Free(vmRuntime.get)
      self ! Complete(code, "")
      goto(CompleteExecution) using InCompleteExecution()

    /** Starts the procedure for creating a new shell instance */
    case Event(CreateLocalShell(), InScriptExecution(scriptEnv, thread)) =>
      implicit val logQualifier = LogEntryQualifier("ScriptExecution_CreateLocalShell")
      env.runtime ! Runtime.Commands.Spawn("shell", Vector.empty, SystemCommandDefinition(""), self, "script")
      goto(ShellSpawn) using InShellSpawn(sender(), scriptEnv, thread)

    /** This section of the code process the singnal from runtime. The essence of processing comes down to the
      * fact that a new signal is added to the runtime script. If the runtime script has a permissions flag to
      * interrupt the thread, the script thread will be is interrupted for out from any blocking states. All
      * operations on runtime data of the script are protected by a common mutex with the script, which prevents
      * the script code from moving to the critical section until the data is completed */
    //ONLY MANUAL TESTING
    case Event(CmdExecutor.ControlMessages.Signal(code), InScriptExecution(scriptEnv, thread)) =>
      scriptEnv.runtime.sigQueue.offer(code)
      scriptEnv.runtime.mayInterruptedMutex.acquire()
      if (scriptEnv.runtime.mayInterrupted.get())
        thread.interrupt()
      scriptEnv.runtime.mayInterruptedMutex.release()
      stay
  }

  /** Processing the response from the runtime about the results of creating a new shell process */
  when(ShellSpawn, if (testMode) 0.5 second else 10 second) {

    /** Successful creation of the shell process. The description of the created shell is sent to the originator */
    case Event(d: ProcessConstructor.Responses.ProcessDef, InShellSpawn(sEnv, scriptEnv, thread)) =>
      implicit val logQualifier = LogEntryQualifier("ShellSpawn_ProcessDef")
      logger.debug("Success creation of the new shell instance")
      d.ref ! Process.Commands.Start
      sEnv ! ShellDefinition(d.ref, d.streams(1), d.streams(0))
      goto(ScriptExecution) using InScriptExecution(scriptEnv, thread)

    /**Error creating shell process. An error message is sent to the originator */
    case Event(Runtime.Responses.SpawnError, InShellSpawn(sEnv, scriptEnv, thread)) =>
      implicit val logQualifier = LogEntryQualifier("ShellSpawn_SpawnError")
      logger.error("Shell instance spawn error")
      sEnv ! ShellCreationError
      goto(ScriptExecution) using InScriptExecution(scriptEnv, thread)

    /** Runtime does not respond. An error message is sent to the originator. */
    case Event(StateTimeout, InShellSpawn(sEnv, scriptEnv, thread)) =>
      implicit val logQualifier = LogEntryQualifier("ShellSpawn_StateTimeout")
      logger.error("Runtime respond timeout")
      sEnv ! ShellCreationError
      goto(ScriptExecution) using InScriptExecution(scriptEnv, thread)
  }

  /** The final execution point of the executor. In this state, only one message is accepted: Complete. It is directed
    * by the actor to herself before going into this state. The message handler writes the message to the standard
    * output with the terminator eof. Then the process terminates. */
  when(CompleteExecution, 5 second) {
    case Event(Complete(code, message), _) =>
      implicit val logQualifier = LogEntryQualifier("CompleteExecution_Complete")
      logger.info(s"Command '${args(0)} ${args.slice(2, args.size).toSpacedString}' completed with code $code ${if (code != 0) s" / $message" else ""}")
      streams(0).tell(Stream.Commands.WriteWrapped(ByteString(message).eof.exit(code)), ActorRef.noSender)
      process ! CommandsUnion.Responses.ExecutorFinished(code)
      stop

    //NOT TESTABLE
    case Event(StateTimeout, _) =>
      implicit val logQualifier = LogEntryQualifier("CompleteExecution_StateTimeout")
      logger.warning("Very strange timeout")
      streams(0).tell(Stream.Commands.WriteWrapped(ByteString("Internal error 2")), ActorRef.noSender)
      streams(0).tell(Stream.Commands.WriteWrapped(ByteString().eof.exit(1)), ActorRef.noSender)
      process ! CommandsUnion.Responses.ExecutorFinished(1)
      stop
  }

  initialize()
}

