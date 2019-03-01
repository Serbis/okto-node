package ru.serbis.okto.node.unit.hardware

import akka.actor.ActorSystem
import akka.stream._
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import akka.util.ByteString
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import ru.serbis.okto.node.common.Env
import ru.serbis.okto.node.events.{Eventer, HardwareEvent}
import ru.serbis.okto.node.hardware.{CmdReplicator, RfBridge, SerialBridge}
import ru.serbis.okto.node.hardware.CmdReplicator.Supply.{ReplicationPair, SourceBridge}
import ru.serbis.okto.node.hardware.packets.EventConfirmator
import ru.serbis.okto.node.log.Logger.LogLevels
import ru.serbis.okto.node.log.{StdOutLogger, StreamLogger}

import scala.concurrent.duration._


class CmdReplicatorSpec extends TestKit(ActorSystem("TestSystem")) with ImplicitSender with WordSpecLike with Matchers with StreamLogger with BeforeAndAfterAll {

  implicit val mater = ActorMaterializer()

  override protected def beforeAll(): Unit = {
    initializeGlobalLogger(system, LogLevels.Info)
    logger.addDestination(system.actorOf(StdOutLogger.props, "StdOutLogger"))
  }

  "CmdReplicator" must {
    "Process positive test for rf bridge" in {
      val (target, eventer, rfBridge, _, _) = createTestSet()

      //For address cash
      target ! CmdReplicator.Commands.Replic(0x11111101, "gpio_r 0", SourceBridge.RfBridge)
      target ! CmdReplicator.Commands.Replic(0x11111101, "gpio_r 0", SourceBridge.RfBridge)
      target ! CmdReplicator.Commands.Replic(0x11111101, "gpio_r 1", SourceBridge.RfBridge)
      target ! CmdReplicator.Commands.Replic(0x11111101, "gpio_r 2", SourceBridge.RfBridge)
      target ! CmdReplicator.Commands.Replic(0x11111102, "tcc", SourceBridge.RfBridge)
      target ! CmdReplicator.Commands.Replic(0x11111104, "gpio_r 0", SourceBridge.RfBridge)
      target ! CmdReplicator.Commands.Replic(0x11111109, "gpio_r 0", SourceBridge.RfBridge)

      eventer.send(target, HardwareEvent(100, 99, 0x11111101, confirmed = false, ByteString.empty))
      rfBridge.expectMsgAllOf(
        RfBridge.Commands.ExbCommand(0x11111101, "gpio_r 0", 3000),
        RfBridge.Commands.ExbCommand(0x11111101, "gpio_r 1", 3000),
        RfBridge.Commands.ExbCommand(0x11111101, "gpio_r 2", 3000)
      )
      rfBridge.reply(RfBridge.Responses.ExbResponse("_", "cmdr0"))
      rfBridge.reply(RfBridge.Responses.ExbResponse("_", "cmdr1"))
      rfBridge.reply(RfBridge.Responses.ExbResponse("_", "cmdr2"))

      eventer.send(target, HardwareEvent(100, 99, 0x11111102, confirmed = false, ByteString.empty))
      rfBridge.expectMsg(RfBridge.Commands.ExbCommand(0x11111102, "tcc", 3000))
      rfBridge.reply(RfBridge.Responses.ExbResponse("_", "cmdr3"))
      rfBridge.expectNoMessage(0.5 second)

      eventer.send(target, HardwareEvent(100, 99, 0x11111104, confirmed = false, ByteString.empty))
      rfBridge.expectMsg(RfBridge.Commands.ExbCommand(0x11111104, "gpio_r 0", 3000))
      rfBridge.reply(RfBridge.Responses.ExbResponse("_", "cmdr4"))
      rfBridge.expectNoMessage(0.5 second)

      eventer.send(target, HardwareEvent(100, 99, 0x11111108, confirmed = false, ByteString.empty))
      rfBridge.expectNoMessage(0.5 second)


      //For global cash
      target ! CmdReplicator.Commands.Replic(15, "gpio_w 0 1", SourceBridge.RfBridge)
      target ! CmdReplicator.Commands.Replic(16, "gpio_w 1 1", SourceBridge.RfBridge)
      target ! CmdReplicator.Commands.Replic(17, "gpio_w 2 1", SourceBridge.RfBridge)
      eventer.send(target, HardwareEvent(100, 99, 15, confirmed = false, ByteString.empty))
      eventer.send(target, HardwareEvent(100, 99, 16, confirmed = false, ByteString.empty))
      eventer.send(target, HardwareEvent(100, 99, 17, confirmed = false, ByteString.empty))
      rfBridge.expectMsgAllOf(
        RfBridge.Commands.ExbCommand(15, "gpio_w 0 1", 3000),
        RfBridge.Commands.ExbCommand(16, "gpio_w 1 1", 3000),
        RfBridge.Commands.ExbCommand(17, "gpio_w 2 1", 3000)
      )
      rfBridge.reply(RfBridge.Responses.ExbResponse("_", "cmdr5"))
      rfBridge.reply(RfBridge.Responses.ExbResponse("_", "cmdr6"))
      rfBridge.reply(RfBridge.Responses.ExbResponse("_", "cmdr7"))
    }

    "Process positive test for serial bridge" in {
      val (target, eventer, _, serialBridge, _) = createTestSet()

      //For address cash
      target ! CmdReplicator.Commands.Replic(0x11111101, "gpio_r 0", SourceBridge.SerialBridge)
      target ! CmdReplicator.Commands.Replic(0x11111101, "gpio_r 0", SourceBridge.SerialBridge)
      target ! CmdReplicator.Commands.Replic(0x11111101, "gpio_r 1", SourceBridge.SerialBridge)
      target ! CmdReplicator.Commands.Replic(0x11111101, "gpio_r 2", SourceBridge.SerialBridge)
      target ! CmdReplicator.Commands.Replic(0x11111102, "tcc", SourceBridge.SerialBridge)
      target ! CmdReplicator.Commands.Replic(0x11111104, "gpio_r 0", SourceBridge.SerialBridge)
      target ! CmdReplicator.Commands.Replic(0x11111109, "gpio_r 0", SourceBridge.SerialBridge)

      eventer.send(target, HardwareEvent(100, 99, 0x11111101, confirmed = false, ByteString.empty))
      serialBridge.expectMsgAllOf(
        SerialBridge.Commands.ExbCommand("gpio_r 0", 3000),
        SerialBridge.Commands.ExbCommand("gpio_r 1", 3000),
        SerialBridge.Commands.ExbCommand("gpio_r 2", 3000)
      )
      serialBridge.reply(SerialBridge.Responses.ExbResponse("_", "cmdr0"))
      serialBridge.reply(SerialBridge.Responses.ExbResponse("_", "cmdr1"))
      serialBridge.reply(SerialBridge.Responses.ExbResponse("_", "cmdr2"))

      eventer.send(target, HardwareEvent(100, 99, 0x11111102, confirmed = false, ByteString.empty))
      serialBridge.expectMsg(SerialBridge.Commands.ExbCommand("tcc", 3000))
      serialBridge.reply(SerialBridge.Responses.ExbResponse("_", "cmdr3"))
      serialBridge.expectNoMessage(0.5 second)

      eventer.send(target, HardwareEvent(100, 99, 0x11111104, confirmed = false, ByteString.empty))
      serialBridge.expectMsg(SerialBridge.Commands.ExbCommand("gpio_r 0", 3000))
      serialBridge.reply(SerialBridge.Responses.ExbResponse("_", "cmdr4"))
      serialBridge.expectNoMessage(0.5 second)

      eventer.send(target, HardwareEvent(100, 99, 0x11111108, confirmed = false, ByteString.empty))
      serialBridge.expectNoMessage(0.5 second)


      //For global cash
      target ! CmdReplicator.Commands.Replic(0, "gpio_w 0 1", SourceBridge.SerialBridge)
      target ! CmdReplicator.Commands.Replic(0, "gpio_w 1 1", SourceBridge.SerialBridge)
      target ! CmdReplicator.Commands.Replic(0, "gpio_w 2 1", SourceBridge.SerialBridge)
      eventer.send(target, HardwareEvent(100, 99, 0, confirmed = false, ByteString.empty))
      eventer.send(target, HardwareEvent(100, 99, 0, confirmed = false, ByteString.empty))
      eventer.send(target, HardwareEvent(100, 99, 0, confirmed = false, ByteString.empty))
      serialBridge.expectMsgAllOf(
        SerialBridge.Commands.ExbCommand("gpio_w 0 1", 3000),
        SerialBridge.Commands.ExbCommand("gpio_w 1 1", 3000),
        SerialBridge.Commands.ExbCommand("gpio_w 2 1", 3000)
      )
      serialBridge.reply(SerialBridge.Responses.ExbResponse("_", "cmdr5"))
      serialBridge.reply(SerialBridge.Responses.ExbResponse("_", "cmdr6"))
      serialBridge.reply(SerialBridge.Responses.ExbResponse("_", "cmdr7"))
    }

    "Retry send command if bridge does not respond" in {
      val (target, eventer, _, serialBridge, _) = createTestSet(tm = true)
      target ! CmdReplicator.Commands.Replic(0x11111101, "gpio_r 0", SourceBridge.SerialBridge)
      eventer.send(target, HardwareEvent(100, 99, 0x11111101, confirmed = false, ByteString.empty))
      serialBridge.expectMsg(SerialBridge.Commands.ExbCommand("gpio_r 0", 3000))
      serialBridge.expectMsg(SerialBridge.Commands.ExbCommand("gpio_r 0", 3000))
      serialBridge.expectMsg(SerialBridge.Commands.ExbCommand("gpio_r 0", 3000))
      serialBridge.expectMsg(SerialBridge.Commands.ExbCommand("gpio_r 0", 3000))
      serialBridge.expectNoMessage(1 second)
    }

    "Don't process new startup event while all commands reach the device" in {
      val (target, eventer, _, serialBridge, _) = createTestSet(tm = true)
      target ! CmdReplicator.Commands.Replic(0x11111101, "gpio_r 0", SourceBridge.SerialBridge)
      eventer.send(target, HardwareEvent(100, 99, 0x11111101, confirmed = false, ByteString.empty))
      eventer.send(target, HardwareEvent(100, 99, 0x11111101, confirmed = false, ByteString.empty))
      serialBridge.expectMsg(SerialBridge.Commands.ExbCommand("gpio_r 0", 3000))
      serialBridge.expectMsg(SerialBridge.Commands.ExbCommand("gpio_r 0", 3000))
      serialBridge.expectMsg(SerialBridge.Commands.ExbCommand("gpio_r 0", 3000))
      serialBridge.expectMsg(SerialBridge.Commands.ExbCommand("gpio_r 0", 3000))
      serialBridge.expectNoMessage(1 second)
    }
  }

  def createTestSet(tm: Boolean = false) = {
    val eventer = TestProbe()
    val probe = TestProbe()
    val rfBridge = TestProbe()
    val serialBridge = TestProbe()
    val matrix = List(
      ReplicationPair(None, "gpio_w [0-9] [0-9]"),
      ReplicationPair(None, "tca"),
      ReplicationPair(None, "tcb"),
      ReplicationPair(Some(0x11111101), "gpio_r"),
      ReplicationPair(Some(0x11111102), "tcc"),
      ReplicationPair(Some(0x11111103), "tcd"),
      ReplicationPair(Some(0x11111104), "gpio_r")
    )
    val target = system.actorOf(CmdReplicator.props(matrix, eventer.ref, tm))
    eventer.expectMsg(Eventer.Commands.Subscribe(Some(100), target))
    target ! CmdReplicator.Commands.SetBridges(CmdReplicator.Supply.BridgeRefs(serialBridge.ref, rfBridge.ref))

    (target, eventer, rfBridge, serialBridge, probe)
  }
}