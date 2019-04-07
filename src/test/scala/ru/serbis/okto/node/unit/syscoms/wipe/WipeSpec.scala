package ru.serbis.okto.node.unit.syscoms.wipe

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import akka.util.ByteString
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import ru.serbis.okto.node.common.ReachTypes.ReachByteString
import ru.serbis.okto.node.common.{CommandsUnion, Env}
import ru.serbis.okto.node.hardware.NsdBridge
import ru.serbis.okto.node.log.{StdOutLogger, StreamLogger}
import ru.serbis.okto.node.runtime.Stream
import ru.serbis.okto.node.syscoms.wipe.Wipe

class WipeSpec extends TestKit(ActorSystem("TestSystem")) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll with StreamLogger {


  override protected def beforeAll(): Unit = {
    initializeGlobalLogger(system)
    logger.addDestination(system.actorOf(StdOutLogger.props))
  }

  "Wipe" must {
    "Process positive test" in {
      val probe = TestProbe()
      val stdOut = TestProbe()
      val stdIn = TestProbe()
      val nsdBridge = TestProbe()
      val process = TestProbe()
      val target = system.actorOf(Wipe.props(Env(nsdBridge = nsdBridge.ref), Vector.empty, null))

      probe.send(target, CommandsUnion.Commands.Run(process.ref,  Map(0 -> stdOut.ref, 1 -> stdIn.ref)))
      stdOut.expectMsg(Stream.Commands.WriteWrapped(ByteString("Type 'yes' for complete operation").prompt))
      stdIn.send(target, Stream.Responses.Data(ByteString("yes").eoi))
      nsdBridge.expectMsg(NsdBridge.Commands.SendCommand("wipe"))
      nsdBridge.reply(NsdBridge.Responses.NsdResult(""))
      stdOut.expectMsg(Stream.Commands.WriteWrapped(ByteString("OK").eoi.eof.exit(0)))
    }

    "Complete with error if user type not yes" in {
      val probe = TestProbe()
      val stdOut = TestProbe()
      val stdIn = TestProbe()
      val nsdBridge = TestProbe()
      val process = TestProbe()
      val target = system.actorOf(Wipe.props(Env(nsdBridge = nsdBridge.ref), Vector.empty, null))

      probe.send(target, CommandsUnion.Commands.Run(process.ref,  Map(0 -> stdOut.ref, 1 -> stdIn.ref)))
      stdOut.expectMsg(Stream.Commands.WriteWrapped(ByteString("Type 'yes' for complete operation").prompt))
      stdIn.send(target, Stream.Responses.Data(ByteString("no").eoi))
      stdOut.expectMsg(Stream.Commands.WriteWrapped(ByteString("Operation canceled").eoi.eof.exit(1)))
    }

    "Complete with error if user does not present yes with expected timeout" in {
      val probe = TestProbe()
      val stdOut = TestProbe()
      val stdIn = TestProbe()
      val nsdBridge = TestProbe()
      val process = TestProbe()
      val target = system.actorOf(Wipe.props(Env(nsdBridge = nsdBridge.ref), Vector.empty, null, testMode = true))

      probe.send(target, CommandsUnion.Commands.Run(process.ref,  Map(0 -> stdOut.ref, 1 -> stdIn.ref)))
      stdOut.expectMsg(Stream.Commands.WriteWrapped(ByteString("Type 'yes' for complete operation").prompt))

      stdOut.expectMsg(Stream.Commands.WriteWrapped(ByteString("User input timeout").eoi.eof.exit(2)))
    }

    "Return error if NsdBridge respond with transaction timeout" in {
      val probe = TestProbe()
      val stdOut = TestProbe()
      val stdIn = TestProbe()
      val nsdBridge = TestProbe()
      val process = TestProbe()
      val target = system.actorOf(Wipe.props(Env(nsdBridge = nsdBridge.ref), Vector.empty, null))

      probe.send(target, CommandsUnion.Commands.Run(process.ref,  Map(0 -> stdOut.ref, 1 -> stdIn.ref)))
      stdOut.expectMsg(Stream.Commands.WriteWrapped(ByteString("Type 'yes' for complete operation").prompt))
      stdIn.send(target, Stream.Responses.Data(ByteString("yes").eoi))
      nsdBridge.expectMsg(NsdBridge.Commands.SendCommand("wipe"))
      nsdBridge.reply(NsdBridge.Responses.TransactionTimeout)
      stdOut.expectMsg(Stream.Commands.WriteWrapped(ByteString("NSD does not respond").eoi.eof.exit(3)))
    }

    "Return error if NsdBridge respond with nsd error" in {
      val probe = TestProbe()
      val stdOut = TestProbe()
      val stdIn = TestProbe()
      val nsdBridge = TestProbe()
      val process = TestProbe()
      val target = system.actorOf(Wipe.props(Env(nsdBridge = nsdBridge.ref), Vector.empty, null))

      probe.send(target, CommandsUnion.Commands.Run(process.ref,  Map(0 -> stdOut.ref, 1 -> stdIn.ref)))
      stdOut.expectMsg(Stream.Commands.WriteWrapped(ByteString("Type 'yes' for complete operation").prompt))
      stdIn.send(target, Stream.Responses.Data(ByteString("yes").eoi))
      nsdBridge.expectMsg(NsdBridge.Commands.SendCommand("wipe"))
      nsdBridge.reply(NsdBridge.Responses.NsdError(10, "err"))
      stdOut.expectMsg(Stream.Commands.WriteWrapped(ByteString("NSD error 10 -> err").eoi.eof.exit(4)))
    }

    "Return error if boot repository does not respond with expected timeout" in {
      val probe = TestProbe()
      val stdOut = TestProbe()
      val stdIn = TestProbe()
      val nsdBridge = TestProbe()
      val process = TestProbe()
      val target = system.actorOf(Wipe.props(Env(nsdBridge = nsdBridge.ref), Vector.empty, null, testMode = true))

      probe.send(target, CommandsUnion.Commands.Run(process.ref,  Map(0 -> stdOut.ref, 1 -> stdIn.ref)))
      stdOut.expectMsg(Stream.Commands.WriteWrapped(ByteString("Type 'yes' for complete operation").prompt))
      stdIn.send(target, Stream.Responses.Data(ByteString("yes").eoi))
      stdOut.expectMsg(Stream.Commands.WriteWrapped(ByteString("Bridge timeout").eoi.eof.exit(5)))
    }
  }
}