package ru.serbis.okto.node.unit.hardware

import akka.actor.{ActorSystem, PoisonPill}
import akka.stream._
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import akka.util.ByteString
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import ru.serbis.okto.node.events.{Eventer, HardwareEvent}
import ru.serbis.okto.node.hardware.CmdReplicator.Supply.SourceBridge
import ru.serbis.okto.node.hardware.{CmdReplicator, RfBridge}
import ru.serbis.okto.node.hardware.RfBridge.Responses.ExbResponse
import ru.serbis.okto.node.hardware.packets.{EventConfirmator, ExbPacket, WsdPacket}
import ru.serbis.okto.node.hardware.packets.ExbPacket.{apply => _, _}
import ru.serbis.okto.node.hardware.packets.WsdPacket._
import ru.serbis.okto.node.log.Logger.LogLevels
import ru.serbis.okto.node.log.{StdOutLogger, StreamLogger}
import ru.serbis.okto.node.proxy.napi.TestNativeApiProxy
import ru.serbis.okto.node.proxy.system.TestActorSystemProxy

import scala.concurrent.duration._


class RfBridgeSpec extends TestKit(ActorSystem("TestSystem")) with ImplicitSender with WordSpecLike with Matchers with StreamLogger with BeforeAndAfterAll {

  implicit val mater = ActorMaterializer()

  override protected def beforeAll(): Unit = {
    initializeGlobalLogger(system, LogLevels.Info)
    logger.addDestination(system.actorOf(StdOutLogger.props, "StdOutLogger"))
  }

  "After start must create new new socket and request wsd packet" in {
    val napProbe = TestProbe()
    val napProxy = new TestNativeApiProxy(napProbe.ref)

    system.actorOf(RfBridge.props("/tmp/wsd.socket", 100,1 second, napProxy, null, null, null))

    val sockPath = napProbe.expectMsgType[TestNativeApiProxy.Actions.UnixDomainConnect]
    sockPath.path.deep == ByteString("/tmp/wsd.socket").toArray.deep shouldEqual true

    napProbe.reply(5)
    napProbe.expectMsg(TestNativeApiProxy.Actions.UnixDomainReadWsdPacket(5, 1000))
  }

  "After stop must stop reader thread and close socket" in {
    val probe = TestProbe()
    val (target, napProbe, _, _) = completeTarget()

    target ! PoisonPill
    probe.expectNoMessage(0.5 second)
    napProbe.reply(Array.empty[Byte])
    napProbe.expectMsg(TestNativeApiProxy.Actions.UnixDomainClose(5))
    napProbe.reply()
  }

  "For ExbCommand transaction" must {
    "Process positive test" in {
      val probe = TestProbe()
      val (target, napProbe, _, cmdReplicator) = completeTarget()

      val readerSender = napProbe.lastSender
      probe.send(target, RfBridge.Commands.ExbCommand(499, "abc", 1000))
      cmdReplicator.expectMsg(CmdReplicator.Commands.Replic(499, "abc", SourceBridge.RfBridge))
      val toPacket = napProbe.expectMsgType[TestNativeApiProxy.Actions.UnixDomainWrite]
      toPacket.sd shouldEqual 5
      toPacket.s.deep shouldEqual WsdTransmitPacket(1, 499, ExbCommandPacket(1, "abc")).toArray.deep
      napProbe.reply(0)
      napProbe.send(readerSender, WsdReceivePacket(1, 499, ExbResponsePacket(1, "def")).toArray)
      probe.expectMsg(RfBridge.Responses.ExbResponse("def", "-"))
    }

    "Respond with TransactionTimeout if driver does not respond" in {
      val probe = TestProbe()
      val (target, napProbe, _, _) = completeTarget(0.4 second)

      probe.send(target, RfBridge.Commands.ExbCommand(499, "abc", 500))
      napProbe.expectMsgType[TestNativeApiProxy.Actions.UnixDomainWrite]
      napProbe.reply(0)
      probe.expectMsg(RfBridge.Responses.TransactionTimeout)
    }

    "Respond with BridgeOverload if maxReq table is full" in {
      val probe = TestProbe()
      val (target, napProbe, _, _) = completeTarget(maxReq = 0)

      probe.send(target, RfBridge.Commands.ExbCommand(499, "abc", 500))
      probe.expectMsg(RfBridge.Responses.BridgeOverload)
    }

    "Respond with ExbBrokenResponse if exb send broken package" in {
      val probe = TestProbe()
      val (target, napProbe, _, _) = completeTarget()

      val readerSender = napProbe.lastSender
      probe.send(target, RfBridge.Commands.ExbCommand(499, "abc", 1000))
      val toPacket = napProbe.expectMsgType[TestNativeApiProxy.Actions.UnixDomainWrite]
      toPacket.sd shouldEqual 5
      toPacket.s.deep shouldEqual WsdTransmitPacket(1, 499, ExbCommandPacket(1, "abc")).toArray.deep
      napProbe.reply(0)
      napProbe.send(readerSender, WsdReceivePacket(1, 499, ByteString(Array.fill(50)(0 toByte))).toArray)
      probe.expectMsg(RfBridge.Responses.ExbBrokenResponse)
    }

    "Respond with ExbUnreachable if driver respond with unreachable error" in {
      val probe = TestProbe()
      val (target, napProbe, _, _) = completeTarget()

      val readerSender = napProbe.lastSender
      probe.send(target, RfBridge.Commands.ExbCommand(499, "abc", 1000))
      val toPacket = napProbe.expectMsgType[TestNativeApiProxy.Actions.UnixDomainWrite]
      toPacket.sd shouldEqual 5
      toPacket.s.deep shouldEqual WsdTransmitPacket(1, 499, ExbCommandPacket(1, "abc")).toArray.deep
      napProbe.reply(0)
      napProbe.send(readerSender, WsdErrorPacket(1, WsdPacket.Constants.ERROR_ADDR_UNREACHABLE, "").toArray)
      probe.expectMsg(RfBridge.Responses.ExbUnreachable)
    }

    "Respond with ExbAddrNotDefined if driver respond with address error" in {
      val probe = TestProbe()
      val (target, napProbe, _, _) = completeTarget()

      val readerSender = napProbe.lastSender
      probe.send(target, RfBridge.Commands.ExbCommand(499, "abc", 1000))
      val toPacket = napProbe.expectMsgType[TestNativeApiProxy.Actions.UnixDomainWrite]
      toPacket.sd shouldEqual 5
      toPacket.s.deep shouldEqual WsdTransmitPacket(1, 499, ExbCommandPacket(1, "abc")).toArray.deep
      napProbe.reply(0)
      napProbe.send(readerSender, WsdErrorPacket(1, WsdPacket.Constants.ERROR_ADDR_NOT_DEFINED, "").toArray)
      probe.expectMsg(RfBridge.Responses.ExbAddrNotDefined)
    }

    "Respond with DriverError if driver respond with unexpected error" in {
      val probe = TestProbe()
      val (target, napProbe, _, _) = completeTarget()

      val readerSender = napProbe.lastSender
      probe.send(target, RfBridge.Commands.ExbCommand(499, "abc", 1000))
      val toPacket = napProbe.expectMsgType[TestNativeApiProxy.Actions.UnixDomainWrite]
      toPacket.sd shouldEqual 5
      toPacket.s.deep shouldEqual WsdTransmitPacket(1, 499, ExbCommandPacket(1, "abc")).toArray.deep
      napProbe.reply(0)
      napProbe.send(readerSender, WsdErrorPacket(1, 999, "err").toArray)
      probe.expectMsg(RfBridge.Responses.DriverError(999, "err"))
    }

    "Respond with ExbError if exp respond with error packet" in {
      val probe = TestProbe()
      val (target, napProbe, _, _) = completeTarget()

      val readerSender = napProbe.lastSender
      probe.send(target, RfBridge.Commands.ExbCommand(499, "abc", 1000))
      val toPacket = napProbe.expectMsgType[TestNativeApiProxy.Actions.UnixDomainWrite]
      toPacket.sd shouldEqual 5
      toPacket.s.deep shouldEqual WsdTransmitPacket(1, 499, ExbCommandPacket(1, "abc")).toArray.deep
      napProbe.reply(0)
      napProbe.send(readerSender, WsdReceivePacket(1, 499, ExbErrorPacket(1, 9999, "err")).toArray)
      probe.expectMsg(RfBridge.Responses.ExbError(9999, "err", "-"))
    }
  }

  "For events logic" must {
    "Process EventAck command" in {
      val probe = TestProbe()
      val (target, napProbe, _, _) = completeTarget()

      probe.send(target, EventConfirmator.Responses.EventAck(99, 199, result = true))
      val toPacket = napProbe.expectMsgType[TestNativeApiProxy.Actions.UnixDomainWrite]
      toPacket.sd shouldEqual 5
      toPacket.s.deep shouldEqual WsdTransmitPacket(0, 199, ExbEventAckPacket(99, 0)).toArray.deep
    }


    "Send non confirmed event to the eventer" in {
      val (_, napProbe, eventer, _) = completeTarget()

      val readerSender = napProbe.lastSender
      napProbe.send(readerSender, WsdReceivePacket(1, 499, ExbEventPacket(10, 99, confirmed = false, ByteString("abc"))).toArray)
      eventer.expectMsg(Eventer.Commands.Receive(HardwareEvent(99, 10, 499, confirmed = false, ByteString("abc"))))
    }

    "Run event confirmator for confirmed event" in {
      val confirmator = TestProbe()
      val systemProbe = TestProbe()
      val (_, napProbe, _, _) = completeTarget(systemProxy = new TestActorSystemProxy(systemProbe.ref, system))

      val readerSender = napProbe.lastSender
      napProbe.send(readerSender, WsdReceivePacket(10, 499, ExbEventPacket(10, 99, confirmed = true, ByteString("abc"))).toArray)

      val props = systemProbe.expectMsgType[TestActorSystemProxy.Actions.ActorOf]
      props.props.actorClass() + "$" shouldEqual EventConfirmator.getClass.toString
      systemProbe.reply(confirmator.ref)
      confirmator.expectMsg(EventConfirmator.Commands.Exec(HardwareEvent(99, 10, 499, confirmed = true, ByteString("abc"))))
    }
  }

  "For SetPipeMatrix transaction" must {
    "Process positive test" in {
      val probe = TestProbe()
      val (target, napProbe, _, _) = completeTarget()

      val readerSender = napProbe.lastSender
      val matrix = RfBridge.Commands.PipeMatrix(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
      probe.send(target, RfBridge.Commands.SetPipeMatrix(matrix))
      val toPacket = napProbe.expectMsgType[TestNativeApiProxy.Actions.UnixDomainWrite]
      toPacket.sd shouldEqual 5
      toPacket.s.deep shouldEqual WsdSetPipeMatrixPacket(1, matrix.toWsdPipeMatrix).toArray.deep
      napProbe.reply(0)
      napProbe.send(readerSender, WsdResultPacket(1, ByteString(0)).toArray)
      probe.expectMsg(RfBridge.Responses.SuccessDriverOperation)
    }

    "Respond with TransactionTimeout if driver does not respond" in {
      val probe = TestProbe()
      val (target, napProbe, _, _) = completeTarget(0.4 second, testMode = true)

      val matrix = RfBridge.Commands.PipeMatrix(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
      probe.send(target, RfBridge.Commands.SetPipeMatrix(matrix))
      napProbe.expectMsgType[TestNativeApiProxy.Actions.UnixDomainWrite]
      napProbe.reply(0)
      probe.expectMsg(RfBridge.Responses.TransactionTimeout)
    }

    "Respond with BadPipeMatrix if driver respond with broken pipe matrix error" in {
      val probe = TestProbe()
      val (target, napProbe, _, _) = completeTarget()

      val readerSender = napProbe.lastSender
      val matrix = RfBridge.Commands.PipeMatrix(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
      probe.send(target, RfBridge.Commands.SetPipeMatrix(matrix))
      val toPacket = napProbe.expectMsgType[TestNativeApiProxy.Actions.UnixDomainWrite]
      toPacket.sd shouldEqual 5
      toPacket.s.deep shouldEqual WsdSetPipeMatrixPacket(1, matrix.toWsdPipeMatrix).toArray.deep
      napProbe.reply(0)
      napProbe.send(readerSender, WsdErrorPacket(1, WsdPacket.Constants.ERROR_BROKEN_PIPE_MATRIX, "").toArray)
      probe.expectMsg(RfBridge.Responses.BadPipeMatrix)
    }

    "Respond with BadPipeLsb if driver respond with bad lsb error" in {
      val probe = TestProbe()
      val (target, napProbe, _, _) = completeTarget()

      val readerSender = napProbe.lastSender
      val matrix = RfBridge.Commands.PipeMatrix(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
      probe.send(target, RfBridge.Commands.SetPipeMatrix(matrix))
      val toPacket = napProbe.expectMsgType[TestNativeApiProxy.Actions.UnixDomainWrite]
      toPacket.sd shouldEqual 5
      toPacket.s.deep shouldEqual WsdSetPipeMatrixPacket(1, matrix.toWsdPipeMatrix).toArray.deep
      napProbe.reply(0)
      napProbe.send(readerSender, WsdErrorPacket(1, WsdPacket.Constants.ERROR_BAD_PIPE_MSB, "").toArray)
      probe.expectMsg(RfBridge.Responses.BadPipeMsb)
    }

    "Respond with ChipNotRespond if driver respond with chip not respond error" in {
      val probe = TestProbe()
      val (target, napProbe, _, _) = completeTarget()

      val readerSender = napProbe.lastSender
      val matrix = RfBridge.Commands.PipeMatrix(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
      probe.send(target, RfBridge.Commands.SetPipeMatrix(matrix))
      val toPacket = napProbe.expectMsgType[TestNativeApiProxy.Actions.UnixDomainWrite]
      toPacket.sd shouldEqual 5
      toPacket.s.deep shouldEqual WsdSetPipeMatrixPacket(1, matrix.toWsdPipeMatrix).toArray.deep
      napProbe.reply(0)
      napProbe.send(readerSender, WsdErrorPacket(1, WsdPacket.Constants.ERROR_CHIP_NOT_RESPOND, "").toArray)
      probe.expectMsg(RfBridge.Responses.ChipNotRespond)
    }
  }

  def completeTarget(cleanerTime: FiniteDuration = 1 second, systemProxy: TestActorSystemProxy = null, maxReq: Int = 100, testMode: Boolean = false) = {
    val eventer = TestProbe()
    val cmdReplicator = TestProbe()
    val napProbe = TestProbe()
    val napProxy = new TestNativeApiProxy(napProbe.ref)

    val target = system.actorOf(RfBridge.props("/tmp/wsd.socket", maxReq, cleanerTime, napProxy, eventer.ref, cmdReplicator.ref, systemProxy, testMode))

    val sockPath = napProbe.expectMsgType[TestNativeApiProxy.Actions.UnixDomainConnect]
    sockPath.path.deep == ByteString("/tmp/wsd.socket").toArray.deep shouldEqual true

    napProbe.reply(5)
    napProbe.expectMsg(TestNativeApiProxy.Actions.UnixDomainReadWsdPacket(5, 1000))

    (target, napProbe, eventer, cmdReplicator)
  }
}