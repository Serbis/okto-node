package ru.serbis.okto.node.syscoms

import akka.actor.{ActorRef, Props}
import akka.util.ByteString
import ru.serbis.okto.node.common.FsmDefaults.{Data, State}
import ru.serbis.okto.node.common.ReachTypes.ReachVector
import ru.serbis.okto.node.common.{CommandsUnion, Env}
import ru.serbis.okto.node.hardware.SerialBridge
import ru.serbis.okto.node.log.Logger.LogEntryQualifier
import ru.serbis.okto.node.runtime.{CmdExecutor, Stream}
import ru.serbis.okto.node.runtime.Stream.Responses.{BufferIsFull, Written}
import ru.serbis.okto.node.runtime.StreamControls._
import ru.serbis.okto.node.testut.{ActorSystemExpander, RealActorSystem}

import scala.concurrent.duration._

/** Implementation of the logic of the system command
  *
  * Name : readAdc
  * Args: input - adc line number
  * Action: Reads the value from the analog-to-digital converter of the GPIO line of the expansion board and return it
  *         to StdOut
  * Exit codes: 0 - normal program termination
  *             1 - not enough arguments
  *             2 - StdOut buffer is full
  *             3 - StdOut does not respond
  *             4 - Expansion board response timeout
  *             5 - Hardware error
  *             6 - Expansion board overload
  *             7 - Serial bridge is not responding
  *
  */
object ReadAdc {

  /** n/c */
  def props(env: Env, args: Vector[String], systemEx: ActorSystemExpander = new RealActorSystem, testMode: Boolean = false) =
    Props(new ReadAdc(env, args, systemEx, testMode))

  object States {
    /** see description of the state code */
    case object Idle extends State

    /** see description of the state code */
    case object WaitEbResponse extends State

    /** see description of the state code */
    case object WaitStdOutResponse extends State
  }

  object StatesData {
    /** n/c */
    case object Uninitialized extends Data

    /** n/c */
    case class InWaitEbResponse() extends Data

    /** @param retCode process return code
      * @param message terminal message for log and StdOut */
    case class InWaitStdOutResponse(retCode: Int, message: String) extends Data
  }
}

class ReadAdc(env: Env, args: Vector[String], systemEx: ActorSystemExpander, testMode: Boolean) extends CmdExecutor(systemEx, testMode) {
  import ReadAdc.States._
  import ReadAdc.StatesData._

  setLogSourceName(s"ReadAdc*${self.path.name}")
  setLogKeys(Seq("ReadAdc"))

  var process = ActorRef.noSender
  var streams = Map.empty[Int, ActorRef]

  startWith(Idle, Uninitialized)

  logger.debug("Command logic initialized")

  /** Starting state. Sends the command 'adcRead' to the serial port layer and goes into the answer waiting mode.
    * If the command does not have arguments, the program with code 1 ends.
    */
  when(Idle, 5 second) {
    case Event(req: CommandsUnion.Commands.Run, _) =>
      implicit val logQualifier = LogEntryQualifier("Idle_Run")
      process = req.process
      streams = req.streams

      if (args.nonEmpty) {
        env.serialBridge ! SerialBridge.Commands.SerialRequest(s"readAdc ${args(0)}", 458)
        goto(WaitEbResponse) using InWaitEbResponse()
      } else {
        streams(0) ! Stream.Commands.WriteWrapped(ByteString(s"Not enough arguments") ++ ByteString(Array(EOF)))
        goto(WaitStdOutResponse) using InWaitStdOutResponse(1, "Not enough arguments")
      }

    //NOT TESTABLE
    case Event(StateTimeout, _) =>
      implicit val logQualifier = LogEntryQualifier("Idle_StateTimeout")
      logger.warning("FSM doest not run with expected timeout")
      stop
  }

  /** Waits for a response from the serial port layer. Depending on the response level, it outputs to StdOut and
    * terminates the command with the appropriate code
    */
  when(WaitEbResponse, if (testMode) 0.5 second else 5 second) {
    case Event(SerialBridge.Responses.SerialResponse(resp, 458), _) =>
      streams(0) ! Stream.Commands.WriteWrapped(ByteString(resp) ++ ByteString(Array(EOF)))
      goto(WaitStdOutResponse) using InWaitStdOutResponse(0, "")

    case Event(SerialBridge.Responses.ResponseTimeout(458), _) =>
      streams(0) ! Stream.Commands.WriteWrapped(ByteString("Expansion board response timeout") ++ ByteString(Array(EOF)))
      goto(WaitStdOutResponse) using InWaitStdOutResponse(4, "Expansion board response timeout")

    case Event(SerialBridge.Responses.HardwareError(reason, 458), _) =>
      streams(0) ! Stream.Commands.WriteWrapped(ByteString(s"Hardware error: $reason") ++ ByteString(Array(EOF)))
      goto(WaitStdOutResponse) using InWaitStdOutResponse(5, s"Hardware error: $reason")

    case Event(SerialBridge.Responses.BridgeOverload(458), _) =>
      streams(0) ! Stream.Commands.WriteWrapped(ByteString("Expansion board overload") ++ ByteString(Array(EOF)))
      goto(WaitStdOutResponse) using InWaitStdOutResponse(6, "Expansion board overload")

    case Event(StateTimeout, _) =>
      streams(0) ! Stream.Commands.WriteWrapped(ByteString("Serial bridge is not responding") ++ ByteString(Array(EOF)))
      goto(WaitStdOutResponse) using InWaitStdOutResponse(7, "Serial bridge is not responding")

  }

  /** The response received from stream_0. If the data was successfully written to the stream, the command
    * process is notified of the exit code early detected. If the data is not written to the stream due to its overflow,
    * the command process is notified about the exit code 2. If the stream does not respond within the specified
    * timeout, the command process is notified of the exitcode 3.
    */
  when(WaitStdOutResponse, if (testMode) 0.5 second else 5 second) {
    case Event(Written, data: InWaitStdOutResponse) =>
      implicit val logQualifier = LogEntryQualifier("WaitStdOutResponse_Written")

      logger.info(s"Command 'readAdc ${args.toSpacedString}' completed with code ${data.retCode} ${if (data.retCode != 0) s" / ${data.message}"}")
      process ! CommandsUnion.Responses.ExecutorFinished(data.retCode)
      stop

    case Event(BufferIsFull, data: InWaitStdOutResponse) =>
      implicit val logQualifier = LogEntryQualifier("WaitStdOutResponse_BufferIsFull")
      logger.warning(s"Command 'readAdc ${args.toSpacedString}' completed with code 2 / StdOut buffer is full")
      process ! CommandsUnion.Responses.ExecutorFinished(2)
      stop

    case Event(StateTimeout, _: InWaitStdOutResponse) =>
      implicit val logQualifier = LogEntryQualifier("WaitStdOutResponse_StateTimeout")
      logger.error(s"Command 'readAdc ${args.toSpacedString}' completed with code 3 / StdOut does not respond")
      process ! CommandsUnion.Responses.ExecutorFinished(3)
      stop
  }

  initialize()
}

