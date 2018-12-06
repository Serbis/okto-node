package ru.serbis.okto.node.unit.syscoms

import java.io.File

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import akka.util.ByteString
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import ru.serbis.okto.node.common.{CommandsUnion, Env}
import ru.serbis.okto.node.log.{StdOutLogger, StreamLogger}
import ru.serbis.okto.node.runtime.Stream
import ru.serbis.okto.node.syscoms.Echo
import ru.serbis.okto.node.proto.{messages => proto_messages}
import ru.serbis.okto.node.common.ReachTypes.ReachByteString
import ru.serbis.okto.node.runtime.StreamControls._

class EchoSpec extends TestKit(ActorSystem("TestSystem")) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll with StreamLogger {

  val commandsDat = new File("fect/commands.dat")

  override protected def beforeAll(): Unit = {
    initializeGlobalLogger(system)
    logger.addDestination(system.actorOf(StdOutLogger.props))
  }

  "Echo command must" must {
    "Process command logic for correct conditions" in {
      val stream0 = TestProbe()
      val process = TestProbe()

      val target = system.actorOf(Echo.props(Env(), Vector("xxx")))
      target ! CommandsUnion.Commands.Run(process.ref, Map(0 -> stream0.ref))
      stream0.expectMsg(Stream.Commands.WriteWrapped(ByteString("xxx") ++ ByteString(Array(EOF))))
      stream0.reply(Stream.Responses.Written)
      process.expectMsg(CommandsUnion.Responses.ExecutorFinished(0))
    }

    "Process command logic for insufficient args conditions" in {
      val stream0 = TestProbe()
      val process = TestProbe()

      val target = system.actorOf(Echo.props(Env(), Vector.empty))
      target ! CommandsUnion.Commands.Run(process.ref, Map(0 -> stream0.ref))
      stream0.expectMsg(Stream.Commands.WriteWrapped(ByteString("Not enough arguments") ++ ByteString(Array(EOF))))
      stream0.reply(Stream.Responses.Written)
      process.expectMsg(CommandsUnion.Responses.ExecutorFinished(1))
    }

    "Complete command with code 2 if a stream error writing occurs" in {
      val probe = TestProbe()
      val stream0 = TestProbe()
      val process = TestProbe()

      val target = system.actorOf(Echo.props(Env(), Vector.empty))
      target ! CommandsUnion.Commands.Run(process.ref, Map(0 -> stream0.ref))
      stream0.expectMsg(Stream.Commands.WriteWrapped(ByteString("Not enough arguments") ++ ByteString(Array(EOF))))
      stream0.reply(Stream.Responses.BufferIsFull)
      process.expectMsg(CommandsUnion.Responses.ExecutorFinished(2))
    }


    "Complete command with code 3 if a stream does not respond with expected timeout" in {
      val stream0 = TestProbe()
      val process = TestProbe()

      val target = system.actorOf(Echo.props(Env(), Vector.empty, testMode = true))
      target ! CommandsUnion.Commands.Run(process.ref, Map(0 -> stream0.ref))
      stream0.expectMsg(Stream.Commands.WriteWrapped(ByteString("Not enough arguments") ++ ByteString(Array(EOF))))
      process.expectMsg(CommandsUnion.Responses.ExecutorFinished(3))
    }
  }
}
