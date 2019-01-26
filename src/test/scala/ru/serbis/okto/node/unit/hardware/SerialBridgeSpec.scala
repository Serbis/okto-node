package ru.serbis.okto.node.unit.hardware

import akka.actor.{ActorSystem, PoisonPill}
import akka.stream._
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import akka.util.ByteString
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import ru.serbis.okto.node.hardware.SerialBridge
import ru.serbis.okto.node.hardware.packets.ExbPacket.{ExbCommandPacket, ExbErrorPacket, ExbResponsePacket}
import ru.serbis.okto.node.hardware.packets.WsdPacket
import ru.serbis.okto.node.hardware.packets.WsdPacket.{WsdErrorPacket, WsdReceivePacket, WsdTransmitPacket}
import ru.serbis.okto.node.log.Logger.LogLevels
import ru.serbis.okto.node.log.{StdOutLogger, StreamLogger}
import ru.serbis.okto.node.proxy.napi.TestNativeApiProxy

import scala.concurrent.duration._


class SerialBridgeSpec extends TestKit(ActorSystem("TestSystem")) with ImplicitSender with WordSpecLike with Matchers with StreamLogger with BeforeAndAfterAll {

  implicit val mater = ActorMaterializer()

  override protected def beforeAll(): Unit = {
    initializeGlobalLogger(system, LogLevels.Info)
    logger.addDestination(system.actorOf(StdOutLogger.props, "StdOutLogger"))
  }

  "After start must open uart device and request exb packet" in {
    val napProbe = TestProbe()
    val napProxy = new TestNativeApiProxy(napProbe.ref)

    system.actorOf(SerialBridge.props("/dev/ttyS0", 115200, 100,1 second, napProxy))

    val devOpen = napProbe.expectMsgType[TestNativeApiProxy.Actions.SerialOpen]
    devOpen.device.deep == ByteString("/dev/ttyS0").toArray.deep shouldEqual true
    devOpen.baud shouldEqual 115200

    napProbe.reply(5)
    napProbe.expectMsg(TestNativeApiProxy.Actions.SerialReadExbPacket(5, 1000))
  }

  "After stop must stop reader thread and close socket" in {
    val probe = TestProbe()
    val (target, napProbe) = completeTarget()

    target ! PoisonPill
    probe.expectNoMessage(0.5 second)
    napProbe.reply(Array.empty[Byte])
    napProbe.expectMsg(TestNativeApiProxy.Actions.SerialClose(5))
    napProbe.reply()
  }

  "For ExbCommand transaction" must {
    "Process positive test" in {
      val probe = TestProbe()
      val (target, napProbe) = completeTarget()

      val readerSender = napProbe.lastSender
      probe.send(target, SerialBridge.Commands.ExbCommand("abc", 1000))
      val toPacket = napProbe.expectMsgType[TestNativeApiProxy.Actions.SerialPuts]
      toPacket.fd shouldEqual 5
      toPacket.s.deep shouldEqual ExbCommandPacket(1, "abc").toArray.deep
      napProbe.reply(0)
      napProbe.send(readerSender, ExbResponsePacket(1, "def").toArray)
      probe.expectMsg(SerialBridge.Responses.ExbResponse("def"))
    }

    "Respond with TransactionTimeout if driver does not respond" in {
      val probe = TestProbe()
      val (target, napProbe) = completeTarget(0.4 second)

      probe.send(target, SerialBridge.Commands.ExbCommand("abc", 500))
      napProbe.expectMsgType[TestNativeApiProxy.Actions.SerialPuts]
      napProbe.reply(0)
      probe.expectMsg(SerialBridge.Responses.TransactionTimeout)
    }

    "Respond with BridgeOverload if maxReq table is full" in {
      val probe = TestProbe()
      val (target, napProbe) = completeTarget(maxReq = 0)

      probe.send(target, SerialBridge.Commands.ExbCommand("abc", 500))
      probe.expectMsg(SerialBridge.Responses.BridgeOverload)
    }

    "Respond with ExbError if exp respond with error packet" in {
      val probe = TestProbe()
      val (target, napProbe) = completeTarget()

      val readerSender = napProbe.lastSender
      probe.send(target, SerialBridge.Commands.ExbCommand("abc", 1000))
      val toPacket = napProbe.expectMsgType[TestNativeApiProxy.Actions.SerialPuts]
      toPacket.fd shouldEqual 5
      toPacket.s.deep shouldEqual ExbCommandPacket(1, "abc").toArray.deep
      napProbe.reply(0)
      napProbe.send(readerSender, ExbErrorPacket(1, 9999, "err").toArray)
      probe.expectMsg(SerialBridge.Responses.ExbError(9999, "err"))
    }


  }

  def completeTarget(cleanerTime: FiniteDuration = 1 second, maxReq: Int = 100) = {
    val napProbe = TestProbe()
    val napProxy = new TestNativeApiProxy(napProbe.ref)

    val target = system.actorOf(SerialBridge.props("/tmp/wsd.socket", 115200, maxReq, cleanerTime, napProxy))

    val devOpen = napProbe.expectMsgType[TestNativeApiProxy.Actions.SerialOpen]
    devOpen.device.deep == ByteString("/tmp/wsd.socket").toArray.deep shouldEqual true
    devOpen.baud shouldEqual 115200

    napProbe.reply(5)
    napProbe.expectMsg(TestNativeApiProxy.Actions.SerialReadExbPacket(5, 1000))

    (target, napProbe)
  }
}