package ru.serbis.okto.node.syscoms.reboot

import akka.actor.{ActorRef, Props}
import akka.util.ByteString
import ru.serbis.okto.node.common.FsmDefaults.{Data, State}
import ru.serbis.okto.node.common.ReachTypes.{ReachByteString, ReachVector}
import ru.serbis.okto.node.common.{CommandsUnion, Env}
import ru.serbis.okto.node.hardware.NsdBridge
import ru.serbis.okto.node.log.Logger.LogEntryQualifier
import ru.serbis.okto.node.runtime.{CmdExecutor, Stream}
import ru.serbis.okto.node.testut.{ActorSystemExpander, RealActorSystem}
import scala.concurrent.duration._

/**
  * Reboot node hardware
  */
object Reboot {

  def props(env: Env, args: Vector[String], systemEx: ActorSystemExpander = new RealActorSystem, testMode: Boolean = false) =
    Props(new Reboot(env, args, systemEx, testMode))

  object States {
    case object Idle extends State
    case object WaitResult extends State
    case object CompleteExecution extends State
  }

  object StatesData {
    case object Uninitialized extends Data
    case object InWaitResult extends Data
    case object InCompleteExecution extends Data
  }

  object Internals {

    /** Self sent message in state CompleteExecution (or it may be send be inner fsm). For detail, see state description
      *
      * @param code exit code
      * @param message exit message
      */
    case class Complete(code: Int, message: String)
  }
}

class Reboot(env: Env, args: Vector[String], systemEx: ActorSystemExpander, testMode: Boolean) extends CmdExecutor(systemEx, testMode) {
  import Reboot.Internals._
  import Reboot.States._
  import Reboot.StatesData._

  setLogSourceName(s"Reboot*${self.path.name}")
  setLogKeys(Seq("Reboot"))

  var process = ActorRef.noSender
  var streams = Map.empty[Int, ActorRef]

  startWith(Idle, Uninitialized)

  logger.debug("Command logic initialized")

  /** Start stage */
  when(Idle, 5 second) {

    /** Send reboot command to the nsd bridge and go to waiting response from it */
    case Event(req: CommandsUnion.Commands.Run, _) =>
      implicit val logQualifier = LogEntryQualifier("Idle_Run")
      process = req.process
      streams = req.streams

      env.nsdBridge ! NsdBridge.Commands.SendCommand("reboot")

      goto(WaitResult) using InWaitResult


    //NOT TESTABLE
    case Event(StateTimeout, _) =>
      implicit val logQualifier = LogEntryQualifier("Idle_StateTimeout")
      logger.warning("FSM doest not run with expected timeout")
      stop
  }

  /** Wait response from the nsd bridge */
  when(WaitResult, if (testMode) 0.5 second else 10 second) {

    /** Ok, nsd respond with success */
    case Event(NsdBridge.Responses.NsdResult(_), _) =>
      implicit val logQualifier = LogEntryQualifier("WaitResult_NsdResult")
      self ! Complete(0, "OK")
      goto(CompleteExecution) using InCompleteExecution

    /** Nsd does not respond and fail transaction by timeout */
    case Event(NsdBridge.Responses.TransactionTimeout, _) =>
      implicit val logQualifier = LogEntryQualifier("WaitResult_TransactionTimeout")
      self ! Complete(1, "NSD does not respond")
      goto(CompleteExecution) using InCompleteExecution

    /** Nsd respond with come application error */
    case Event(NsdBridge.Responses.NsdError(code, msg), _) =>
      implicit val logQualifier = LogEntryQualifier("WaitResult_NsdError")
      self ! Complete(2, s"NSD error $code -> $msg")
      goto(CompleteExecution) using InCompleteExecution

    /** Bridge does not respond */
    case Event(StateTimeout, _) =>
      implicit val logQualifier = LogEntryQualifier("WaitResult_StateTimeout")
      self ! Complete(3, "Bridge timeout")
      goto(CompleteExecution) using InCompleteExecution
  }

  /** The final execution point of the command. In this state, only one message is accepted: Complete. It is directed
    * by the actor to herself before going into this state. The message handler writes the message to the standard
    * output with the terminators eoi-eof. Then the process terminates. */
  when(CompleteExecution, 60 second) {
    case Event(Complete(code, message), _) =>
      implicit val logQualifier = LogEntryQualifier("CompleteExecution_Complete")
      logger.info(s"Command 'reboot ${args.slice(1, args.size).toSpacedString}' completed with code $code ${if (code != 0) s" / $message" else ""}")
      streams(0).tell(Stream.Commands.WriteWrapped(ByteString(message).eoi.eof.exit(code)), ActorRef.noSender)
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

