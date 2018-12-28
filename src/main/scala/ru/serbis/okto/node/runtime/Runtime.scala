package ru.serbis.okto.node.runtime

import java.util.UUID

import akka.actor.{Actor, ActorRef, Props}
import ru.serbis.okto.node.common.Env
import ru.serbis.okto.node.log.Logger.LogEntryQualifier
import ru.serbis.okto.node.log.StreamLogger
import ru.serbis.okto.node.runtime.ProcessConstructor.Responses.ProcessDef

import scala.collection.mutable

/** The actor releases control functions for the node's runtime. It contains a process pool and manages the issuance of process IDs.*/
object Runtime {
  /** @param env node's env object
    */
  def props(env: Env) = Props(new Runtime(env))

  object Commands {

    /** Creates a new process for the command specified in the arguments. If successful, returns the ID of the created process
      * in the Pid message, otherwise the SpawnError message is returned
      *
      * @param cmd command name
      * @param args command arguments
      * @param cmdDef command configuration object (command definition in config)
      * @param initiator command initiator (read StdOut receiver)
      * @param subsystem what initialize process spawn(shell, tunnel, boot and etc)
      */
    case class Spawn(cmd: String, args: Vector[String], cmdDef: Any, initiator: ActorRef, subsystem: String)

    /** Adds a process def to the pool by its identifier and responds with the Injected message. The process identifier
      * must be reserved beforehand through the ReservePid message. Otherwise, this request will be answered by PidNotReserved
      *
      * @param procDef process definition
      */
    case class Inject(procDef: ProcessDef)

    /** Remove precess from pool by it's identifier. Respond with the Process message, containing the removed process reference.
      * If precess with passed id does not reserved, respond with ProcessNotRegistered message.
      *
      * @param pid process identifier */
    case class RemoveProcess(pid: Int)

    /** Returns the process reference by its identifier in the Process message
      *
      * @param pid process identifier */
    case class GetProcess(pid: Int)

    /** Reserve new free process id. Responds with a Pid message containing a reserved process identifier */
    case object ReservePid

    /** Return definitions list of the all run processes. Respond with ProcessesDefs message
      *
      * @param pids process identifiers. If this param is None, all defs will be returned
      */
    case class GetProcessesDefs(pids: Option[List[Int]])

    /** Send signal to the specified process executor. Signal is the CmdExecutor.ControlMessages.Signal message Respond with
      * SignalSent if success or ProcessNotRegistered if process with the specified pid does not exist
      *
      * @param pid process identified
      * @param signal signal code
      */
    case class SendSignal(pid: Int, signal: Int)
  }

  object Responses {
    /** Process identifier value
      *
      * @param id identifier */
    case class Pid(id: Int)

    /** Process reference container
      *
      * @param ref process reference. This value may by is None, if pid was reserved, but process reference are not yet injected
      */
    case class Process(ref: Option[ActorRef])

    /** Error creating new process. This message is indicative of the fact that in some of the subject entities a critical error
      * occurred that does not allow the creation of a new process. */
    case object SpawnError

    /** Process reference was successfully injected by Intject message */
    case object Injected

    /** Attempting to perform an operation with a non-existent process */
    case object ProcessNotRegistered

    /** Attempting to perform an operation with a non-existent pid */
    case object PidNotReserved

    /** Processes definitions */
    case class ProcessesDefs(defs: List[ProcessDef])

    /** Success signal sending operation */
    case object SignalSent
  }
}

class Runtime(env2: Env) extends Actor with StreamLogger {
  import Runtime.Commands._
  import Runtime.Responses._

  setLogSourceName(s"Runtime*${self.path.name}")
  setLogKeys(Seq("Runtime"))

  implicit val logQualifier = LogEntryQualifier("static")

  val env = env2.copy(runtime = context.self)

  logger.info("Runtime actor is initialized")

  /** Process pool */
  val procPool = mutable.HashMap.empty[Int, Option[ProcessDef]]

  /** Processes id's counter*/
  var pidCounter = 0

  override def receive: Receive = {

    /** see message description */
    case ReservePid =>
      implicit val logQualifier = LogEntryQualifier("ReservePid")
      logger.debug(s"Requested pid reservation from '${sender().path}'")
      def inject: Int = {
        if (pidCounter == Int.MaxValue) pidCounter = 1
        else pidCounter = pidCounter + 1
        val pid = pidCounter
        if (procPool.contains(pid)) {
          inject
        } else {
          procPool += pid -> None
          pid
        }
      }
      val pid = inject
      logger.debug(s"Reserved new pid '$pid'")
      sender() ! Pid(pid)

    /** see message description */
    case GetProcess(pid) =>
      implicit val logQualifier = LogEntryQualifier("GetProcess")
      logger.debug(s"Requested process reference for pid '$pid' from '${sender().path}'")
      val procDef = procPool.get(pid)
      if (procDef.isDefined) {
        logger.debug(s"Returned ${ if (procDef.get.isDefined) s"fully process reference '${procDef.get.map(v => v.ref.path)}'" else "empty process reference" } ")
        sender() ! Process(procDef.get.map(v => v.ref))
      } else {
        logger.debug("Requested pid is not registered")
        sender() ! ProcessNotRegistered
      }

    /** see message description */
    case RemoveProcess(pid) =>
      implicit val logQualifier = LogEntryQualifier("RemoveProcess")
      logger.debug(s"Requested process removing for pid '$pid' from '${sender().path}'")
      val procDef = procPool.get(pid)
      if (procDef.isDefined) {
        procPool -= pid
        logger.debug(s"${ if (procDef.get.isDefined) "Fully" else "Empty" } process definition was removed from pool")
        sender() ! Process(procDef.get.map(v => v.ref))
      } else {
        logger.debug("Requested pid is not registered. Nothing to remove")
        sender() ! ProcessNotRegistered
      }

    /** see message description */
    case Inject(procDef) =>
      implicit val logQualifier = LogEntryQualifier("Inject")
      logger.debug(s"Requested process injecting '${procDef.ref.path}' for pid '${procDef.pid}' from '${sender().path}'")
      val pd = procPool.get(procDef.pid)
      if (pd.isDefined) {
        procPool(procDef.pid) = Some(procDef)
        logger.debug(s"Injected process reference '${procDef.ref.path} to pid '${procDef.pid}'")
        sender() ! Injected
      } else {
        logger.warning("Requested pid is not reserved")
        sender() ! PidNotReserved
      }

    /** see message description */
    case Spawn(cmd, args, cmdDef, initiator, subsystem) =>
      implicit val logQualifier = LogEntryQualifier("Spawn")
      logger.debug(s"""Requested process spawning for command '$cmd ${args.foldLeft("")((a, v) => a + s" $v")}' from '${sender().path}'""")
      val fsm = context.system.actorOf(SpawnFsm.props(env.copy(runtime = self)))
      logger.debug(s"Spawn procedure is running for command '$cmd ${args.foldLeft("")((a, v) => a + s" $v")}'")
      fsm.tell(SpawnFsm.Commands.Exec(cmd, args, cmdDef, initiator, subsystem), sender())

    /** see message description */
    case GetProcessesDefs(pids) =>
      implicit val logQualifier = LogEntryQualifier("GetProcessesDefs")
      logger.debug(s"Requested processes definition list from actor '${sender().path}'")
      sender() ! ProcessesDefs(procPool.filter(v => {
        if (pids.isDefined) {
          val b = pids.get.contains(v._1) && v._2.isDefined
          println(b)
          b
        } else {
          v._2.isDefined
        }
      }).map(v => {
        v._2.get
      }).toList)

    /** see message description */
    case SendSignal(pid, signal) =>
      implicit val logQualifier = LogEntryQualifier("SendSignal")
      val procDef = procPool.get(pid)
      if (procDef.isDefined) {
        if (procDef.get.isDefined) {
          procDef.get.get.executor ! CmdExecutor.ControlMessages.Signal(signal)
          logger.debug(s"Signal '$signal' was send to the process executor with pid '$pid'")
          sender() ! SignalSent
        } else {
          logger.warning(s"Unable to send signal '$signal' to the process executor with pid '$pid' because the process spawning procedure is not complited")
          sender() ! ProcessNotRegistered
        }
      } else {
        logger.warning(s"Unable to send signal '$signal' to the process executor with pid '$pid' because the process does not exist")
        sender() ! ProcessNotRegistered
      }
  }
}
