package ru.serbis.okto.node.syscoms.wipe

import akka.actor.{ActorRef, Props}
import akka.util.ByteString
import ru.serbis.okto.node.common.FsmDefaults.{Data, State}
import ru.serbis.okto.node.common.ReachTypes.{ReachByteString, ReachVector}
import ru.serbis.okto.node.common.{CommandsUnion, Env}
import ru.serbis.okto.node.hardware.NsdBridge
import ru.serbis.okto.node.log.Logger.LogEntryQualifier
import ru.serbis.okto.node.runtime.{CmdExecutor, Stream, StreamControls}
import ru.serbis.okto.node.testut.{ActorSystemExpander, RealActorSystem}

import scala.concurrent.duration._

/**
  * Wipe node software
  */
object Wipe {

  def props(env: Env, args: Vector[String], systemEx: ActorSystemExpander = new RealActorSystem, testMode: Boolean = false) =
    Props(new Wipe(env, args, systemEx, testMode))

  object States {
    case object Idle extends State
    case object WaitConfirmation extends State
    case object WaitResult extends State
    case object CompleteExecution extends State
  }

  object StatesData {
    case object Uninitialized extends Data
    case class InWaitConfirmation(inBuf: ByteString) extends Data
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

class Wipe(env: Env, args: Vector[String], systemEx: ActorSystemExpander, testMode: Boolean) extends CmdExecutor(systemEx, testMode) {
  import Wipe.States._
  import Wipe.StatesData._
  import Wipe.Internals._

  setLogSourceName(s"Wipe*${self.path.name}")
  setLogKeys(Seq("Wipe"))

  var process = ActorRef.noSender
  var streams = Map.empty[Int, ActorRef]

  startWith(Idle, Uninitialized)

  logger.debug("Command logic initialized")

  /** Start stage */
  when(Idle, 5 second) {

    /** Send request for user confirmation  */
    case Event(req: CommandsUnion.Commands.Run, _) =>
      implicit val logQualifier = LogEntryQualifier("Idle_Run")
      process = req.process
      streams = req.streams

      streams(0) ! Stream.Commands.WriteWrapped(ByteString("Type 'yes' for complete operation").eoi.prompt)

      goto(WaitConfirmation) using InWaitConfirmation(ByteString.empty)


    //NOT TESTABLE
    case Event(StateTimeout, _) =>
      implicit val logQualifier = LogEntryQualifier("Idle_StateTimeout")
      logger.warning("FSM doest not run with expected timeout")
      stop
  }


  /** Wait user confirmation */
  when(WaitConfirmation, if (testMode) 0.5 second else 240 second) {

    /** Collect input data. If it is reservie 'yes' string - send shutdown command to the nsd bridge and go to waiting
      * response from it, else complete command with error */
    case Event(Stream.Responses.Data(bs), data: InWaitConfirmation) =>
      val eoiIndex = bs.indexOf(StreamControls.EOI)
      if (eoiIndex == -1) {
        stay using data.copy(inBuf = data.inBuf ++ bs)
      } else {
        val pt = (data.inBuf ++ bs.slice(0, bs.size - 1)).utf8String
        if (pt == "yes") {
          env.nsdBridge ! NsdBridge.Commands.SendCommand("wipe")
          goto(WaitResult) using InWaitResult
        } else {
          self ! Complete(1, "Operation canceled")
          goto(CompleteExecution) using InCompleteExecution
        }
      }

    case Event(StateTimeout, _) =>
      self ! Complete(2, "User input timeout")
      goto(CompleteExecution) using InCompleteExecution

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
      self ! Complete(3, "NSD does not respond")
      goto(CompleteExecution) using InCompleteExecution

    /** Nsd respond with come application error */
    case Event(NsdBridge.Responses.NsdError(code, msg), _) =>
      implicit val logQualifier = LogEntryQualifier("WaitResult_NsdError")
      self ! Complete(4, s"NSD error $code -> $msg")
      goto(CompleteExecution) using InCompleteExecution

    /** Bridge does not respond */
    case Event(StateTimeout, _) =>
      implicit val logQualifier = LogEntryQualifier("WaitResult_StateTimeout")
      self ! Complete(5, "Bridge timeout")
      goto(CompleteExecution) using InCompleteExecution
  }

  /** The final execution point of the command. In this state, only one message is accepted: Complete. It is directed
    * by the actor to herself before going into this state. The message handler writes the message to the standard
    * output with the terminators eoi-eof. Then the process terminates. */
  when(CompleteExecution, 60 second) {
    case Event(Complete(code, message), _) =>
      implicit val logQualifier = LogEntryQualifier("CompleteExecution_Complete")
      logger.info(s"Command 'wipe ${args.slice(1, args.size).toSpacedString}' completed with code $code ${if (code != 0) s" / $message" else ""}")
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

