package ru.serbis.okto.node.runtime

import akka.actor.SupervisorStrategy.Stop
import akka.actor.{Actor, ActorRef, Props}
import ru.serbis.okto.node.common.{CommandsUnion, Env}
import ru.serbis.okto.node.log.Logger.LogEntryQualifier
import ru.serbis.okto.node.log.StreamLogger

/** Representation of the command process. This actor is engaged in servicing the process of executing a certain command */
object Process {

  /** @param initiator Initiator of the process. Actor, which in the process of executing the command will be sent its
    *                  standard output and to which the exit code will be sent after the completion of the work of the command.
    * @param executor The executive logic of the command. When the process is initiated, it must be in the start-up standby mode.
    * @param streams Process I / O Streams
    * @param pid procres identifier
    */
  def props(env: Env, initiator: ActorRef, executor: ActorRef, streams: Map[Int, ActorRef], pid: Int) =
    Props(new Process(env, initiator, executor, streams, pid))

  object Commands {
    /** Starts the process. This message triggers the execution of the command's executive logic. */
    case object Start

    /** Return process identifier in Pid response */
    case object GetPid

    /** Return process executor in Executor response*/
    case object GetExecutor

    /** Return map with the process i/o streams */
    case object GetStreams
  }

  object Responses {
    /** Process identifier
      *
      * @param value pid value */
    case class Pid(value: Int)

    /** Command logic executor
      *
      * @param ref executor reference
      */
    case class Executor(ref: ActorRef)

    /** Command i / o streams
      *
      * @param value map with streams refs
      */
    case class Streams(value: Map[Int, ActorRef])

    /** The message about the process shutdown. This message is sent to the actor initiator of the process after the completion
      * of the executive logic of the command.
      *
      * @param code exit code
      */
    case class Stopped(code: Int)
  }
}

class Process(env: Env, initiator: ActorRef, executor: ActorRef, streams: Map[Int, ActorRef], pid: Int) extends Actor with StreamLogger {
  import Process.Commands._
  import Process.Responses._

  setLogSourceName(s"Process*${self.path.name}")
  setLogKeys(Seq("Process"))

  implicit val logQualifier = LogEntryQualifier("static")

  override def receive = {
    /** see msg description */
    case Start =>
      implicit val logQualifier = LogEntryQualifier("static")
      logger.debug("Process was triggered to started mode")
      executor ! CommandsUnion.Commands.Run(self, streams)

    /** see msg description */
    case GetPid => sender() ! Pid(pid)

    /** see msg description */
    case GetExecutor => sender() ! Executor(executor)

    /** see msg description */
    case GetStreams => sender() ! Streams(streams)

    /** see msg description */
    case CommandsUnion.Responses.ExecutorFinished(code) =>
      streams.foreach(v => v._2 ! Stream.Commands.FlushedClose)
      logger.debug(s"Process with pid '$pid' was stopped")
      env.runtime ! Runtime.Commands.RemoveProcess(pid)
      initiator ! Stopped(code)
      context.stop(self)
  }
}