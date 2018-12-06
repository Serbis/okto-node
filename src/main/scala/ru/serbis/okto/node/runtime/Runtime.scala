package ru.serbis.okto.node.runtime

import java.util.UUID

import akka.actor.{Actor, ActorRef, Props}
import ru.serbis.okto.node.common.Env
import ru.serbis.okto.node.log.Logger.LogEntryQualifier
import ru.serbis.okto.node.log.StreamLogger

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
      */
    case class Spawn(cmd: String, args: Vector[String], cmdDef: Any, initiator: ActorRef)

    /** Adds a process reference to the pool by its identifier and responds with the Injected message. The process identifier
      * must be reserved beforehand through the ReservePid message. Otherwise, this request will be answered by PidNotReserved
      *
      * @param process process actor reference
      * @param pid reserved process identifier */
    case class Inject(process: ActorRef, pid: Int)

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
  val procPool = mutable.HashMap.empty[Int, Option[ActorRef]]

  override def receive: Receive = {

    /** see message description */
    case ReservePid =>
      implicit val logQualifier = LogEntryQualifier("ReservePid")
      logger.debug(s"Requested pid reservation from '${sender().path}'")
      def inject: Int = {
        val pid = UUID.randomUUID().hashCode()
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
      val process = procPool.get(pid)
      if (process.isDefined) {
        logger.debug(s"Returned ${ if (process.get.isDefined) s"fully process reference '${process.get.get.path}'" else "empty process reference" } ")
        sender() ! Process(process.get)
      } else {
        logger.debug("Requested pid is not registered")
        sender() ! ProcessNotRegistered
      }

    /** see message description */
    case RemoveProcess(pid) =>
      implicit val logQualifier = LogEntryQualifier("RemoveProcess")
      logger.debug(s"Requested process removing for pid '$pid' from '${sender().path}'")
      val process = procPool.get(pid)
      if (process.isDefined) {
        procPool -= pid
        logger.debug(s"${ if (process.get.isDefined) "Fully" else "Empty" } process definition was removed from pool")
        sender() ! Process(process.get)
      } else {
        logger.debug("Requested pid is not registered. Nothing to remove")
        sender() ! ProcessNotRegistered
      }

    /** see message description */
    case Inject(ref, pid) =>
      implicit val logQualifier = LogEntryQualifier("Inject")
      logger.debug(s"Requested process injecting '${ref.path}' for pid '$pid' from '${sender().path}'")
      val process = procPool.get(pid)
      if (process.isDefined) {
        procPool(pid) = Some(ref)
        logger.debug(s"Injected process reference '${ref.path} to pid '$pid'")
        sender() ! Injected
      } else {
        logger.warning("Requested pid is not reserved")
        sender() ! PidNotReserved
      }

    /** see message description */
    case Spawn(cmd, args, cmdDef, initiator) =>
      implicit val logQualifier = LogEntryQualifier("Inject")
      logger.debug(s"""Requested process spawning for command '$cmd ${args.foldLeft("")((a, v) => a + s" $v")}' from '${sender().path}'""")
      val fsm = context.system.actorOf(SpawnFsm.props(env.copy(runtime = self)))
      logger.debug(s"Spawn procedure is running for command '$cmd ${args.foldLeft("")((a, v) => a + s" $v")}'")
      fsm.tell(SpawnFsm.Commands.Exec(cmd, args, cmdDef, initiator), sender())
  }
}
