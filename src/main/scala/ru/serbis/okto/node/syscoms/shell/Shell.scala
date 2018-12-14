package ru.serbis.okto.node.syscoms.shell

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.util.ByteString
import org.parboiled2.ParseError
import ru.serbis.okto.node.common.{CommandsUnion, Env}
import ru.serbis.okto.node.common.FsmDefaults.{Data, State}
import ru.serbis.okto.node.common.ReachTypes.ReachByteString
import ru.serbis.okto.node.log.Logger.LogEntryQualifier
import ru.serbis.okto.node.runtime.Stream.Commands.{Write, WriteWrapped}
import ru.serbis.okto.node.runtime.{CmdExecutor, Stream, StreamControls}
import ru.serbis.okto.node.runtime.StreamControls._
import ru.serbis.okto.node.syscoms.shell.StatementsParser.{PipedNode, Statement}
import ru.serbis.okto.node.common.ReachTypes.ReachList
import ru.serbis.okto.node.syscoms.shell.PipePreparator.Responses.{CommandsNotFound, PipeCircuit}
import ru.serbis.okto.node.testut.{ActorSystemExpander, RealActorSystem}

import scala.concurrent.duration._
import scala.util.{Failure, Success}

//TODO [1] comment
object Shell {
  def props(env: Env, args: Vector[String], systemEx: ActorSystemExpander = new RealActorSystem, testMode: Boolean = false) =
    Props(new Shell(env, args, systemEx, testMode))

  object States {
    /** see description of the state code */
    case object Idle extends State
    /** see description of the state code */
    case object CommandMode extends State
    /** see description of the state code */
    case object StatementsProcessingMode extends State
    /** see description of the state code */
    case object StatementPipeInitMode extends State
    /** see description of the state code */
    case object StatementPipeAttachMode extends State
    /** see description of the state code */
    case object StatementPipeInteractionMode extends State
  }

  object StatesData {
    /** n/c */
    case object Uninitialized extends Data
    /** n/c */
    case class InCommandMode(inBuffer: ByteString = ByteString.empty) extends Data
    /** n/c */
    case class InStatementsProcessingMode(statements: List[Statement]) extends Data
    /** n/c */
    case class InStatementPipeInitMode(statements: List[Statement]) extends Data
    /** n/c */
    case class InStatementPipeInteractionMode(statements: List[Statement], circuit: PipeCircuit) extends Data
    /** n/c */
    case class InStatementPipeAttachMode(statements: List[Statement], circuit: PipeCircuit) extends Data
  }

  object Internals {
    case object ProcessStatement
  }

  object Commands {

    /** Message that realize keep-alive mechanics. It's describe in each state, and does not allow to come state timeout.
      * This message produced by shell tunnel after receive it message Action with type 1 from connection */
    case object KeepAlive

    /** Message that realize shell soft close action. It's describe in each state, and realize correct shell stopping at
      * each situation. This message produced by shell tunnel after receive it message Action with type 1 from connection */
    case object Die
  }
}

class Shell(env: Env, args: Vector[String], systemEx: ActorSystemExpander, testMode: Boolean = false) extends CmdExecutor(systemEx, testMode) {
  import Shell.States._
  import Shell.StatesData._
  import Shell.Internals._
  import Shell.Commands._

  setLogSourceName(s"Shell*${self.path.name}")
  setLogKeys(Seq("Shell"))

  var process = ActorRef.noSender
  var stdIn = ActorRef.noSender
  var stdOut = ActorRef.noSender

  startWith(Idle, Uninitialized)

  logger.debug("Command logic initialized")

  when(Idle, 5 second) {
    case Event(req: CommandsUnion.Commands.Run, _) =>
      implicit val logQualifier = LogEntryQualifier("Idle_Run")
      process = req.process
      stdOut = req.streams(0)
      stdIn = req.streams(1)

      goto(CommandMode) using InCommandMode(ByteString())

    //NO SENSE TEST
    case Event(KeepAlive, _) => stay

    case Event(Die, _) =>
      implicit val logQualifier = LogEntryQualifier("Idle_Die")
      logger.debug("Shell killed initial mode")
      stop

    //NOT TESTABLE
    case Event(StateTimeout, _) =>
      implicit val logQualifier = LogEntryQualifier("Idle_StateTimeout")
      logger.warning("FSM doest not run with expected timeout")
      stop
  }

  when(CommandMode, if (testMode) 1 second else 1 minute) {
    case Event(inData: Stream.Responses.Data, data: InCommandMode) if sender() == stdIn =>
      implicit val logQualifier = LogEntryQualifier("CommandMode_Data")

      val eoi = inData.bs.indexOf(EOI)
      val eof = inData.bs.indexOf(EOF)
      if (eof != -1) {
        logger.info(s"EOF in StdIn detected. Shell stopped with discarding '${inData.bs.size - 1}' bytes")
        process ! CommandsUnion.Responses.ExecutorFinished(0)
        stop
      } else if (eoi == -1) {
        logger.debug(s"Input data was buffered with size '${inData.bs.size}'")
        stay() using data.copy(inBuffer = data.inBuffer ++ inData.bs)
      } else {
        val inString = inData.bs.dropRight(inData.bs.size - eoi).utf8String
        StatementsParser(inString).Flow.run() match {
          case Success(r) =>
            logger.debug(s"Successfully parsed statements '$inString'")
            self ! ProcessStatement
            goto(StatementsProcessingMode) using InStatementsProcessingMode(r.toList)
          case Failure(e: ParseError) =>
            logger.warning(s"Parsing error for input statements '$inString'. Parser respond with '${e.format(inString)}'")
            stdOut.tell(WriteWrapped(ByteString("Wrong statement") ++ ByteString(Array(EOI, EOF, PROMPT))), ActorRef.noSender)
            stay
        }
      }

    //NO SENSE TEST
    case Event(KeepAlive, _) => stay

    case Event(Die, _) =>
      implicit val logQualifier = LogEntryQualifier("CommandMode_Die")
      logger.debug("Shell killed in command mode")
      process ! CommandsUnion.Responses.ExecutorFinished(0)
      stop

    case Event(StateTimeout, _: InCommandMode) =>
      implicit val logQualifier = LogEntryQualifier("CommandMode_StateTimeout")
      logger.info("Idle timeout was reached. Shell was stopped")
      stdOut.tell(WriteWrapped(ByteString(Array(EOF))), ActorRef.noSender)
      process ! CommandsUnion.Responses.ExecutorFinished(0)
      stop
  }

  when(StatementsProcessingMode, if (testMode) 1 second else 10 second) {
    case Event(ProcessStatement, data: InStatementsProcessingMode) =>
      implicit val logQualifier = LogEntryQualifier("StatementsProcessingMode_ProcessStatement")
      data.statements.head match {
        case PipedNode(commands) =>
          val pipePreparator = systemEx.actorOf(PipePreparator.props(env))
          pipePreparator ! PipePreparator.Commands.Exec(commands.toList)
          logger.debug(s"Start processing of the piped statement '$commands'")
          goto(StatementPipeInitMode) using InStatementPipeInitMode(data.statements.tailOrEmpty)
        case t => //NOT TESTABLE
          logger.error(s"Unexpected statement node type '$t'")
          stdOut.tell(WriteWrapped(ByteString("Unable to execute statements due to internal error 4") ++ ByteString(Array(EOI))), ActorRef.noSender)
          process ! CommandsUnion.Responses.ExecutorFinished(0)
          stop
      }

    //NO SENSE TEST
    case Event(KeepAlive, _) => stay

    case Event(Die, _) =>
      implicit val logQualifier = LogEntryQualifier("StatementsProcessingMode_Die")
      logger.debug("Shell killed in statement processing mode")
      process ! CommandsUnion.Responses.ExecutorFinished(0)
      stop

    //NOT TESTABLE
    case Event(StateTimeout, _: InCommandMode) =>
      implicit val logQualifier = LogEntryQualifier("StatementsProcessingMode_StateTimeout")
      logger.warning("Unable to execute statements due to parser respond timeout")
      stdOut.tell(WriteWrapped(ByteString("Unable to execute statements due to internal error 1") ++ ByteString(Array(EOI, EOF))), ActorRef.noSender)
      goto(CommandMode) using InCommandMode()
  } //TODO [4] ограничить максимально количество работающих шеллов

  when(StatementPipeInitMode, if (testMode) 1 second else 10 second) {
    case Event(r: PipePreparator.Responses.PipeCircuit, data: InStatementPipeInitMode) =>
      implicit val logQualifier = LogEntryQualifier("StatementPipeInitMode_PipeCircuit")
      logger.debug("Pipe mode initialization finished")
      goto(StatementPipeInteractionMode) using InStatementPipeInteractionMode(data.statements, r)

    case Event(PipePreparator.Responses.CommandsNotFound(nc), _: InStatementPipeInitMode) =>
      implicit val logQualifier = LogEntryQualifier("StatementPipeInitMode_CommandsNotFound")
      val fnc = nc.foldLeft("")((a, v) => s"$a, $v").drop(2)
      logger.info(s"Unable to create pipe, some commands not found '$fnc'")
      stdOut.tell(WriteWrapped(ByteString(s"Commands not found - $fnc") ++ ByteString().eoi.eop.exit(0)), ActorRef.noSender) //TODO ну вот нехорошо нелвой код возаращать, программы же как бы не заработала, а значит это ошибка
      goto(CommandMode) using InCommandMode()

    case Event(PipePreparator.Responses.InternalError, _: InStatementPipeInitMode) =>
      implicit val logQualifier = LogEntryQualifier("StatementPipeInitMode_InternalError 1")
      logger.info("Unable to execute statements due to internal error 2")
      stdOut.tell(WriteWrapped(ByteString("Unable to execute statements due to internal error 2") ++ ByteString().eoi.eop.exit(0)), ActorRef.noSender)
      goto(CommandMode) using InCommandMode()

    //NO SENSE TEST
    case Event(KeepAlive, _) => stay

    case Event(Die, _) =>
      implicit val logQualifier = LogEntryQualifier("StatementPipeInitMode_Die")
      logger.debug("Shell killed in pipe init mode")
      process ! CommandsUnion.Responses.ExecutorFinished(0)
      stop

    case Event(StateTimeout, _: InStatementPipeInitMode) =>
      implicit val logQualifier = LogEntryQualifier("StatementPipeInitMode_StateTimeout")
      logger.warning("Unable to execute statements due to PipePreparator respond timeout")
      stdOut.tell(WriteWrapped(ByteString("Unable to execute statements due to internal error 3") ++ ByteString().eoi.eop.exit(0)), ActorRef.noSender)
      goto(CommandMode) using InCommandMode()
  }

  when(StatementPipeInteractionMode, if (testMode) 1 second else 1 minute) {
    case Event(Stream.Responses.Data(bs), data: InStatementPipeInteractionMode) if sender() == stdIn =>
      implicit val logQualifier = LogEntryQualifier("StatementPipeInteractionMode_Data")
      logger.debug(s"Received data from shell stdIn '${if (bs.size > 100) "CUTTED" else bs.toHexString}'")
      data.circuit.stdIn.tell(Stream.Commands.Write(bs), ActorRef.noSender)
      stay

    case Event(Stream.Responses.Data(bs), data: InStatementPipeInteractionMode) if sender() == data.circuit.stdOut =>
      implicit val logQualifier = LogEntryQualifier("StatementPipeInteractionMode_Data")
      logger.debug(s"Received data from pipe stdOut '${if (bs.size > 100) "CUTTED" else bs.toHexString}'")
      val eof = bs.indexOf(EOF)
      if (eof == -1) {
        stdOut.tell(Stream.Commands.WriteWrapped(bs), ActorRef.noSender)
        stay
      } else {
        logger.debug("Detected EOF in stream. Pipe is finished")
        val d = Stream.Commands.WriteWrapped(bs.dropRight(bs.size - eof) ++ ByteString(Array(StreamControls.EOP)) ++ bs.slice(eof + 1, bs.size))
        stdOut.tell(d, ActorRef.noSender)
        goto(CommandMode) using InCommandMode()
      }

    case Event(Stream.Responses.Attached, data: InStatementPipeInteractionMode) =>
      implicit val logQualifier = LogEntryQualifier("StatementPipeInteractionMode_StateTimeout")
      logger.debug("Attached self as consumer to pipe stdOut. Flush stream buffer")
      data.circuit.stdOut.tell(Stream.Commands.Flush, ActorRef.noSender)
      stay

    //NO SENSE TEST
    case Event(KeepAlive, _) => stay

    case Event(Die, _) =>
      implicit val logQualifier = LogEntryQualifier("StatementPipeInteractionMode_Die")
      logger.debug("Shell killed in pipe interaction mode")
      process ! CommandsUnion.Responses.ExecutorFinished(0)
      stop

    //TODO [10] см. комментарий ниже
    // Тут вот не очень ясно что должно происходить. Рассмотрим такую ситуацию - запущена некоторая команда и ожидает
    // ввода. А его нет, что должно происходить дальшей не очень очевидно. Сейчас тут установлен сброс в командный режим
    // по истечении небольшого таймаута. Команды сами должны определять таймаут ввода и самозавершаться. Несоблюдение
    // этого правила, приведет к заполнению памяти метвыми процессами. Как вариантрешения данной проблемы, может
    // рассматриваться варинт завершения программы по отправке EOF в ее ввод.
    case Event(StateTimeout, _: InStatementPipeInteractionMode) =>
      implicit val logQualifier = LogEntryQualifier("StatementPipeInteractionMode_StateTimeout")
      logger.warning("Interaction with pipe was stopped by IO operations timeout")
      stdOut.tell(WriteWrapped(ByteString("IO operations timeout") ++ ByteString(Array(EOI, EOF))), ActorRef.noSender)
      goto(CommandMode) using InCommandMode()
  }

  onTransition {
    case StatementPipeInitMode -> StatementPipeInteractionMode =>
      logger.debug("In transit 1")
      nextStateData match {
        case InStatementPipeInteractionMode(_, circuit) =>
          logger.debug("In transit 2")
          circuit.stdOut ! Stream.Commands.Attach(self)
        case _ => //NOT TESTABLE
      }
    case _ -> CommandMode => stdOut.tell(WriteWrapped(ByteString(Array(PROMPT))), ActorRef.noSender)

  }

  initialize()
}