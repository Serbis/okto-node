package ru.serbis.okto.node.unit.hardware

import akka.actor.{ActorSystem, PoisonPill}
import akka.stream._
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import akka.util.ByteString
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import ru.serbis.okto.node.hardware.CmdReplicator.Supply.SourceBridge
import ru.serbis.okto.node.hardware.packets.NsdPacket._
import ru.serbis.okto.node.hardware.packets.NsdPacket
import ru.serbis.okto.node.hardware.{CmdReplicator, NsdBridge}
import ru.serbis.okto.node.log.Logger.LogLevels
import ru.serbis.okto.node.log.{StdOutLogger, StreamLogger}
import ru.serbis.okto.node.proxy.napi.TestNativeApiProxy
import ru.serbis.okto.node.proxy.system.TestActorSystemProxy

import scala.concurrent.duration._


class NsdBridgeSpec extends TestKit(ActorSystem("TestSystem")) with ImplicitSender with WordSpecLike with Matchers with StreamLogger with BeforeAndAfterAll {

  implicit val mater = ActorMaterializer()

  override protected def beforeAll(): Unit = {
    initializeGlobalLogger(system, LogLevels.Info)
    logger.addDestination(system.actorOf(StdOutLogger.props, "StdOutLogger"))
  }

  "After start must create new new socket and request wsd packet" in {
    val napProbe = TestProbe()
    val napProxy = new TestNativeApiProxy(napProbe.ref)

    system.actorOf(NsdBridge.props("/tmp/nsd.socket", 100,1 second, napProxy, null))

    val sockPath = napProbe.expectMsgType[TestNativeApiProxy.Actions.UnixDomainConnect]
    sockPath.path.deep == ByteString("/tmp/nsd.socket").toArray.deep shouldEqual true

    napProbe.reply(5)
    napProbe.expectMsg(TestNativeApiProxy.Actions.UnixDomainReadNsdPacket(5, 1000))
  }

  "After stop must stop reader thread and close socket" in {
    val probe = TestProbe()
    val (target, napProbe) = completeTarget()

    target ! PoisonPill
    probe.expectNoMessage(0.5 second)
    napProbe.reply(Array.empty[Byte])
    napProbe.expectMsg(TestNativeApiProxy.Actions.UnixDomainClose(5))
    napProbe.reply()
  }

  "For SendCmd transaction" must {
    "Process positive test" in {
      val probe = TestProbe()
      val (target, napProbe) = completeTarget()

      val readerSender = napProbe.lastSender
      probe.send(target, NsdBridge.Commands.SendCommand("abc"))
      val toPacket = napProbe.expectMsgType[TestNativeApiProxy.Actions.UnixDomainWrite]
      toPacket.sd shouldEqual 5
      toPacket.s.deep shouldEqual NsdCmdPacket(1, ByteString("abc")).toArray.deep
      napProbe.reply(0)
      napProbe.send(readerSender, NsdResultPacket(1, ByteString("xxx")).toArray)
      probe.expectMsg(NsdBridge.Responses.NsdResult("xxx"))
    }

    "Respond with TransactionTimeout if driver does not respond" in {
      val probe = TestProbe()
      val (target, napProbe) = completeTarget(0.4 second, testMode = true)

      probe.send(target, NsdBridge.Commands.SendCommand("abc"))
      napProbe.expectMsgType[TestNativeApiProxy.Actions.UnixDomainWrite]
      napProbe.reply(0)
      probe.expectMsg(NsdBridge.Responses.TransactionTimeout)
    }

    "Respond with NsdError if driver respond with error result" in {
      val probe = TestProbe()
      val (target, napProbe) = completeTarget()

      val readerSender = napProbe.lastSender
      probe.send(target, NsdBridge.Commands.SendCommand("abc"))
      val toPacket = napProbe.expectMsgType[TestNativeApiProxy.Actions.UnixDomainWrite]
      toPacket.sd shouldEqual 5
      toPacket.s.deep shouldEqual NsdCmdPacket(1, ByteString("abc")).toArray.deep
      napProbe.reply(0)
      napProbe.send(readerSender, NsdErrorPacket(1, 19, "err").toArray)
      probe.expectMsg(NsdBridge.Responses.NsdError(19, "err"))
    }
  }

  def completeTarget(cleanerTime: FiniteDuration = 1 second, systemProxy: TestActorSystemProxy = null, maxReq: Int = 100, testMode: Boolean = false) = {
    val eventer = TestProbe()
    val cmdReplicator = TestProbe()
    val napProbe = TestProbe()
    val napProxy = new TestNativeApiProxy(napProbe.ref)

    val target = system.actorOf(NsdBridge.props("/tmp/nsd.socket", maxReq, cleanerTime, napProxy,systemProxy, testMode))

    val sockPath = napProbe.expectMsgType[TestNativeApiProxy.Actions.UnixDomainConnect]
    sockPath.path.deep == ByteString("/tmp/nsd.socket").toArray.deep shouldEqual true

    napProbe.reply(5)
    napProbe.expectMsg(TestNativeApiProxy.Actions.UnixDomainReadNsdPacket(5, 1000))

    (target, napProbe)
  }
}