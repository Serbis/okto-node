package ru.serbis.okto.node.unit.syscoms

import java.io.File

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import akka.util.ByteString
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import ru.serbis.okto.node.common.{CommandsUnion, Env}
import ru.serbis.okto.node.hardware.SerialBridge
import ru.serbis.okto.node.log.{StdOutLogger, StreamLogger}
import ru.serbis.okto.node.proto.{messages => proto_messages}
import ru.serbis.okto.node.runtime.Stream
import ru.serbis.okto.node.runtime.StreamControls._
import ru.serbis.okto.node.syscoms.{Echo, ReadAdc}

class ReadAdcSpec extends TestKit(ActorSystem("TestSystem")) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll with StreamLogger {

  val commandsDat = new File("fect/commands.dat")

  override protected def beforeAll(): Unit = {
    initializeGlobalLogger(system)
    logger.addDestination(system.actorOf(StdOutLogger.props))
  }

  "Echo command must" must {
    "Im correct conditions command" should {
      "Write value returned by serial bridge to StdOut and complete with code 0" in {
        val stream0 = TestProbe()
        val process = TestProbe()
        val serialBridge = TestProbe()
        val env = Env(serialBridge = serialBridge.ref)

        val target = system.actorOf(ReadAdc.props(env, Vector("10")))
        target ! CommandsUnion.Commands.Run(process.ref, Map(0 -> stream0.ref))
        serialBridge.expectMsg(SerialBridge.Commands.SerialRequest("readAdc 10", 458))
        serialBridge.reply(SerialBridge.Responses.SerialResponse("46555", 458))
        stream0.expectMsg(Stream.Commands.WriteWrapped(ByteString("46555") ++ ByteString(Array(EOF))))
        stream0.reply(Stream.Responses.Written)
        process.expectMsg(CommandsUnion.Responses.ExecutorFinished(0))
      }

      "Write 'Expansion board request timeout' timeout to StdOut if serial bridge respond with RequestTimeout and complete with code 4" in {
        val stream0 = TestProbe()
        val process = TestProbe()
        val serialBridge = TestProbe()
        val env = Env(serialBridge = serialBridge.ref)

        val target = system.actorOf(ReadAdc.props(env, Vector("10")))
        target ! CommandsUnion.Commands.Run(process.ref, Map(0 -> stream0.ref))
        serialBridge.expectMsg(SerialBridge.Commands.SerialRequest("readAdc 10", 458))
        serialBridge.reply(SerialBridge.Responses.ResponseTimeout(458))
        stream0.expectMsg(Stream.Commands.WriteWrapped(ByteString("Expansion board response timeout") ++ ByteString(Array(EOF))))
        stream0.reply(Stream.Responses.Written)
        process.expectMsg(CommandsUnion.Responses.ExecutorFinished(4))
      }

      "Write 'Hardware error: msg' to StdOut if serial bridge respond with HardwareError and complete with code 5" in {
        val stream0 = TestProbe()
        val process = TestProbe()
        val serialBridge = TestProbe()
        val env = Env(serialBridge = serialBridge.ref)

        val target = system.actorOf(ReadAdc.props(env, Vector("10")))
        target ! CommandsUnion.Commands.Run(process.ref, Map(0 -> stream0.ref))
        serialBridge.expectMsg(SerialBridge.Commands.SerialRequest("readAdc 10", 458))
        serialBridge.reply(SerialBridge.Responses.HardwareError("error", 458))
        stream0.expectMsg(Stream.Commands.WriteWrapped(ByteString("Hardware error: error") ++ ByteString(Array(EOF))))
        stream0.reply(Stream.Responses.Written)
        process.expectMsg(CommandsUnion.Responses.ExecutorFinished(5))
      }

      "Write 'Expansion board overload' to StdOut if serial bridge respond with BridgeOverload and complete with code 6" in {
        val stream0 = TestProbe()
        val process = TestProbe()
        val serialBridge = TestProbe()
        val env = Env(serialBridge = serialBridge.ref)

        val target = system.actorOf(ReadAdc.props(env, Vector("10")))
        target ! CommandsUnion.Commands.Run(process.ref, Map(0 -> stream0.ref))
        serialBridge.expectMsg(SerialBridge.Commands.SerialRequest("readAdc 10", 458))
        serialBridge.reply(SerialBridge.Responses.BridgeOverload(458))
        stream0.expectMsg(Stream.Commands.WriteWrapped(ByteString("Expansion board overload") ++ ByteString(Array(EOF))))
        stream0.reply(Stream.Responses.Written)
        process.expectMsg(CommandsUnion.Responses.ExecutorFinished(6))
      }

      "Write 'Serial bridge is not responding' to StdOut if StateTimeout was reached and complete with code 7" in {
        val stream0 = TestProbe()
        val process = TestProbe()
        val serialBridge = TestProbe()
        val env = Env(serialBridge = serialBridge.ref)

        val target = system.actorOf(ReadAdc.props(env, Vector("10"), testMode = true))
        target ! CommandsUnion.Commands.Run(process.ref, Map(0 -> stream0.ref))
        serialBridge.expectMsg(SerialBridge.Commands.SerialRequest("readAdc 10", 458))
        stream0.expectMsg(Stream.Commands.WriteWrapped(ByteString("Serial bridge is not responding") ++ ByteString(Array(EOF))))
        stream0.reply(Stream.Responses.Written)
        process.expectMsg(CommandsUnion.Responses.ExecutorFinished(7))
      }
    }

    "Process command logic for insufficient args conditions" in {
      val stream0 = TestProbe()
      val process = TestProbe()

      val target = system.actorOf(ReadAdc.props(Env(), Vector.empty))
      target ! CommandsUnion.Commands.Run(process.ref, Map(0 -> stream0.ref))
      stream0.expectMsg(Stream.Commands.WriteWrapped(ByteString("Not enough arguments") ++ ByteString(Array(EOF))))
      stream0.reply(Stream.Responses.Written)
      process.expectMsg(CommandsUnion.Responses.ExecutorFinished(1))
    }

    "Complete command with code 2 if a stream error writing occurs" in {
      val stream0 = TestProbe()
      val process = TestProbe()
      val serialBridge = TestProbe()
      val env = Env(serialBridge = serialBridge.ref)

      val target = system.actorOf(ReadAdc.props(env, Vector("10")))
      target ! CommandsUnion.Commands.Run(process.ref, Map(0 -> stream0.ref))
      serialBridge.expectMsg(SerialBridge.Commands.SerialRequest("readAdc 10", 458))
      serialBridge.reply(SerialBridge.Responses.SerialResponse("46555", 458))
      stream0.expectMsg(Stream.Commands.WriteWrapped(ByteString("46555") ++ ByteString(Array(EOF))))
      stream0.reply(Stream.Responses.BufferIsFull)
      process.expectMsg(CommandsUnion.Responses.ExecutorFinished(2))
    }


    "Complete command with code 3 if a stream does not respond with expected timeout" in {
      val stream0 = TestProbe()
      val process = TestProbe()
      val serialBridge = TestProbe()
      val env = Env(serialBridge = serialBridge.ref)

      val target = system.actorOf(ReadAdc.props(env, Vector("10"), testMode = true))
      target ! CommandsUnion.Commands.Run(process.ref, Map(0 -> stream0.ref))
      serialBridge.expectMsg(SerialBridge.Commands.SerialRequest("readAdc 10", 458))
      serialBridge.reply(SerialBridge.Responses.SerialResponse("46555", 458))
      stream0.expectMsg(Stream.Commands.WriteWrapped(ByteString("46555") ++ ByteString(Array(EOF))))
      process.expectMsg(CommandsUnion.Responses.ExecutorFinished(3))
    }
  }
}
