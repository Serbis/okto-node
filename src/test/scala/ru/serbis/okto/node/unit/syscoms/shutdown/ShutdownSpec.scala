package ru.serbis.okto.node.unit.syscoms.shutdown

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import akka.util.ByteString
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import ru.serbis.okto.node.common.ReachTypes.ReachByteString
import ru.serbis.okto.node.common.{CommandsUnion, Env}
import ru.serbis.okto.node.hardware.NsdBridge
import ru.serbis.okto.node.log.{StdOutLogger, StreamLogger}
import ru.serbis.okto.node.runtime.Stream
import ru.serbis.okto.node.syscoms.shutdown.Shutdown

class ShutdownSpec extends TestKit(ActorSystem("TestSystem")) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll with StreamLogger {


  override protected def beforeAll(): Unit = {
    initializeGlobalLogger(system)
    logger.addDestination(system.actorOf(StdOutLogger.props))
  }

  "Shutdown" must {
    "Process positive test" in {
      val probe = TestProbe()
      val stdOut = TestProbe()
      val stdIn = TestProbe()
      val nsdBridge = TestProbe()
      val process = TestProbe()
      val target = system.actorOf(Shutdown.props(Env(nsdBridge = nsdBridge.ref), Vector.empty, null))

      probe.send(target, CommandsUnion.Commands.Run(process.ref,  Map(0 -> stdOut.ref, 1 -> stdIn.ref)))
      nsdBridge.expectMsg(NsdBridge.Commands.SendCommand("shutdown"))
      nsdBridge.reply(NsdBridge.Responses.NsdResult(""))
      stdOut.expectMsg(Stream.Commands.WriteWrapped(ByteString("OK").eoi.eof.exit(0)))
    }

    "Return error if NsdBridge respond with transaction timeout" in {
      val probe = TestProbe()
      val stdOut = TestProbe()
      val stdIn = TestProbe()
      val nsdBridge = TestProbe()
      val process = TestProbe()
      val target = system.actorOf(Shutdown.props(Env(nsdBridge = nsdBridge.ref), Vector.empty, null))

      probe.send(target, CommandsUnion.Commands.Run(process.ref,  Map(0 -> stdOut.ref, 1 -> stdIn.ref)))
      nsdBridge.expectMsg(NsdBridge.Commands.SendCommand("shutdown"))
      nsdBridge.reply(NsdBridge.Responses.TransactionTimeout)
      stdOut.expectMsg(Stream.Commands.WriteWrapped(ByteString("NSD does not respond").eoi.eof.exit(1)))
    }

    "Return error if NsdBridge respond with nsd error" in {
      val probe = TestProbe()
      val stdOut = TestProbe()
      val stdIn = TestProbe()
      val nsdBridge = TestProbe()
      val process = TestProbe()
      val target = system.actorOf(Shutdown.props(Env(nsdBridge = nsdBridge.ref), Vector.empty, null))

      probe.send(target, CommandsUnion.Commands.Run(process.ref,  Map(0 -> stdOut.ref, 1 -> stdIn.ref)))
      nsdBridge.expectMsg(NsdBridge.Commands.SendCommand("shutdown"))
      nsdBridge.reply(NsdBridge.Responses.NsdError(10, "err"))
      stdOut.expectMsg(Stream.Commands.WriteWrapped(ByteString("NSD error 10 -> err").eoi.eof.exit(2)))
    }

    "Return error if boot repository does not respond with expected timeout" in {
      val probe = TestProbe()
      val stdOut = TestProbe()
      val stdIn = TestProbe()
      val nsdBridge = TestProbe()
      val process = TestProbe()
      val target = system.actorOf(Shutdown.props(Env(nsdBridge = nsdBridge.ref), Vector.empty, null, testMode = true))

      probe.send(target, CommandsUnion.Commands.Run(process.ref,  Map(0 -> stdOut.ref, 1 -> stdIn.ref)))
      stdOut.expectMsg(Stream.Commands.WriteWrapped(ByteString("Bridge timeout").eoi.eof.exit(3)))
    }
  }
}