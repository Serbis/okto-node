package ru.serbis.okto.node.unit.adapter

import akka.actor.{ActorRef, ActorSystem}
import akka.http.scaladsl.model.ws.BinaryMessage
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import akka.util.ByteString
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import ru.serbis.okto.node.adapter.ReactiveShellTunnel
import ru.serbis.okto.node.common.{Env, NodeProtoSerializer2}
import ru.serbis.okto.node.log.{StdOutLogger, StreamLogger}
import ru.serbis.okto.node.proto.{messages => proto_messages}
import ru.serbis.okto.node.runtime.{ProcessConstructor, Runtime, Stream, Process}
import ru.serbis.okto.node.common.ReachTypes.ReachByteString
import ru.serbis.okto.node.reps.SyscomsRep.Responses.SystemCommandDefinition
import ru.serbis.okto.node.runtime.StreamControls._


class ReactiveShellTunnelSpec extends TestKit(ActorSystem("TestSystem")) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll with StreamLogger {

  val serializer = NodeProtoSerializer2()

  override protected def beforeAll(): Unit = {
    initializeGlobalLogger(system)
    logger.addDestination(system.actorOf(StdOutLogger.props))
  }

  "ReactiveShellAdapter" must {
    "For Connect message" should {
      "Spawn new shell" in {
        val runtime = TestProbe()
        val env = Env(runtime = runtime.ref)

        val target = system.actorOf(ReactiveShellTunnel.props(env))
        target ! ReactiveShellTunnel.Commands.Connected(ActorRef.noSender)
        runtime.expectMsg(Runtime.Commands.Spawn("shell", Vector.empty, SystemCommandDefinition(""), target))
      }
    }

    "Ror Receive message" should {
      "Buffer data if shell stdIn is not yet defined" in {
        val runtime = TestProbe()
        val shellStdIn = TestProbe()
        val shellStdOut = TestProbe()
        val shellProcess = TestProbe()
        val env = Env(runtime = runtime.ref)

        val target = system.actorOf(ReactiveShellTunnel.props(env))
        target ! ReactiveShellTunnel.Commands.Connected(ActorRef.noSender)
        runtime.expectMsg(Runtime.Commands.Spawn("shell", Vector.empty, SystemCommandDefinition(""), target))
        target ! ReactiveShellTunnel.Commands.Receive(serializer.toBinary(proto_messages.Data(ByteString("abc").toProto)).get)
        runtime.reply(ProcessConstructor.Responses.ProcessDef(shellProcess.ref, 0, Map(0 -> shellStdOut.ref, 1 -> shellStdIn.ref)))
        shellProcess.expectMsg(Process.Commands.Start)
        shellStdIn.expectMsg(Stream.Commands.WriteWrapped(ByteString("abc")))
      }

      "Send deserialized data to shell stdIn" in {
        val runtime = TestProbe()
        val shellStdIn = TestProbe()
        val shellStdOut = TestProbe()
        val shellProcess = TestProbe()
        val env = Env(runtime = runtime.ref)

        val target = system.actorOf(ReactiveShellTunnel.props(env))
        target ! ReactiveShellTunnel.Commands.Connected(ActorRef.noSender)
        runtime.expectMsg(Runtime.Commands.Spawn("shell", Vector.empty, SystemCommandDefinition(""), target))
        runtime.reply(ProcessConstructor.Responses.ProcessDef(shellProcess.ref, 0, Map(0 -> shellStdOut.ref, 1 -> shellStdIn.ref)))
        shellProcess.expectMsg(Process.Commands.Start)
        target ! ReactiveShellTunnel.Commands.Receive(serializer.toBinary(proto_messages.Data(ByteString("abc").toProto)).get)
        shellStdIn.expectMsg(Stream.Commands.WriteWrapped(ByteString("abc")))
      }

      "Don't crash if unexpected message type was received from connection" in {
        //NOT TESTABLE while program has only one message
      }

      "Don't crash if deserialization error was occurred" in {
        val runtime = TestProbe()
        val shellStdIn = TestProbe()
        val shellStdOut = TestProbe()
        val shellProcess = TestProbe()
        val env = Env(runtime = runtime.ref)

        val target = system.actorOf(ReactiveShellTunnel.props(env))
        target ! ReactiveShellTunnel.Commands.Connected(ActorRef.noSender)
        runtime.expectMsg(Runtime.Commands.Spawn("shell", Vector.empty, SystemCommandDefinition(""), target))
        runtime.reply(ProcessConstructor.Responses.ProcessDef(shellProcess.ref, 0, Map(0 -> shellStdOut.ref, 1 -> shellStdIn.ref)))
        shellProcess.expectMsg(Process.Commands.Start)
        target ! ReactiveShellTunnel.Commands.Receive(ByteString("abc"))
        target ! ReactiveShellTunnel.Commands.Receive(serializer.toBinary(proto_messages.Data(ByteString("abc").toProto)).get)
        shellStdIn.expectMsg(Stream.Commands.WriteWrapped(ByteString("abc")))
      }
    }

    "For Data message" should {
      "Serialize and write data to a connection" in {
        val runtime = TestProbe()
        val shellStdIn = TestProbe()
        val shellStdOut = TestProbe()
        val connection = TestProbe()
        val shellProcess = TestProbe()
        val env = Env(runtime = runtime.ref)

        val target = system.actorOf(ReactiveShellTunnel.props(env))
        target ! ReactiveShellTunnel.Commands.Connected(connection.ref)
        runtime.expectMsg(Runtime.Commands.Spawn("shell", Vector.empty, SystemCommandDefinition(""), target))
        runtime.reply(ProcessConstructor.Responses.ProcessDef(shellProcess.ref, 0, Map(0 -> shellStdOut.ref, 1 -> shellStdIn.ref)))
        shellProcess.expectMsg(Process.Commands.Start)
        target ! Stream.Responses.Data(ByteString("abc"))
        connection.expectMsg(BinaryMessage(serializer.toBinary(proto_messages.Data(ByteString("abc").toProto)).get))
      }

      "Don't crash if serialization error was occurred" in {
        //NOT TESTABLE
      }
    }

    "For Close message" should {
      "Send EOF to the shell stdIn if is is was early started" in {
        val runtime = TestProbe()
        val shellStdIn = TestProbe()
        val shellStdOut = TestProbe()
        val connection = TestProbe()
        val shellProcess = TestProbe()
        val env = Env(runtime = runtime.ref)

        val target = system.actorOf(ReactiveShellTunnel.props(env))
        target ! ReactiveShellTunnel.Commands.Connected(connection.ref)
        runtime.expectMsg(Runtime.Commands.Spawn("shell", Vector.empty, SystemCommandDefinition(""), target))
        runtime.reply(ProcessConstructor.Responses.ProcessDef(shellProcess.ref, 0, Map(0 -> shellStdOut.ref, 1 -> shellStdIn.ref)))
        shellProcess.expectMsg(Process.Commands.Start)
        target ! ReactiveShellTunnel.Commands.Close
        shellStdIn.expectMsg(Stream.Commands.Write(ByteString(Array(EOF))))
      }
    }

    "For ProcessDef message" should {
      "Send EOF to the shell stdIn if actor marked as stopped" in {
        val runtime = TestProbe()
        val shellStdIn = TestProbe()
        val shellStdOut = TestProbe()
        val connection = TestProbe()
        val shellProcess = TestProbe()
        val env = Env(runtime = runtime.ref)

        val target = system.actorOf(ReactiveShellTunnel.props(env))
        target ! ReactiveShellTunnel.Commands.Connected(connection.ref)
        runtime.expectMsg(Runtime.Commands.Spawn("shell", Vector.empty, SystemCommandDefinition(""), target))
        target ! ReactiveShellTunnel.Commands.Close
        runtime.reply(ProcessConstructor.Responses.ProcessDef(shellProcess.ref, 0, Map(0 -> shellStdOut.ref, 1 -> shellStdIn.ref)))
        shellStdIn.expectMsg(Stream.Commands.Write(ByteString(Array(EOF))))
      }
    }
  }
}
