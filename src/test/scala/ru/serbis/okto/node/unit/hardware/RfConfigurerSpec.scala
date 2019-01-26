package ru.serbis.okto.node.unit.hardware

import akka.actor.{ActorSystem, PoisonPill}
import akka.stream._
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import akka.util.ByteString
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import ru.serbis.okto.node.hardware.{RfBridge, RfConfigurer}
import ru.serbis.okto.node.hardware.packets.ExbPacket.{ExbCommandPacket, ExbErrorPacket, ExbResponsePacket}
import ru.serbis.okto.node.hardware.packets.WsdPacket
import ru.serbis.okto.node.hardware.packets.WsdPacket._
import ru.serbis.okto.node.log.Logger.LogLevels
import ru.serbis.okto.node.log.{StdOutLogger, StreamLogger}
import ru.serbis.okto.node.proxy.napi.TestNativeApiProxy
import ru.serbis.okto.node.reps.MainRep.Responses.RfConfiguration

import scala.concurrent.duration._


class RfConfigurerSpec extends TestKit(ActorSystem("TestSystem")) with ImplicitSender with WordSpecLike with Matchers with StreamLogger with BeforeAndAfterAll {

  implicit val mater = ActorMaterializer()

  override protected def beforeAll(): Unit = {
    initializeGlobalLogger(system, LogLevels.Info)
    logger.addDestination(system.actorOf(StdOutLogger.props, "StdOutLogger"))
  }

  "RfConfigurer must process positive test" in {
    val probe = TestProbe()
    val rfBridge = TestProbe()
    val config = RfConfiguration(
      "",
      0,
      0,
      "AAAAAA01",
      "AAAAAA11",
      "AAAAAA02",
      "AAAAAA12",
      "AAAAAA03",
      "AAAAAA13",
      "AAAAAA04",
      "AAAAAA14",
      "AAAAAA05",
      "AAAAAA15"
    )

    val target = system.actorOf(RfConfigurer.props(config, rfBridge.ref, testMode = true))
    probe.send(target, RfConfigurer.Commands.Exec())
    val matrix = RfBridge.Commands.PipeMatrix(
      0xAAAAAA01,
      0xAAAAAA11,
      0xAAAAAA02,
      0xAAAAAA12,
      0xAAAAAA03,
      0xAAAAAA13,
      0xAAAAAA04,
      0xAAAAAA14,
      0xAAAAAA05,
      0xAAAAAA15)
    rfBridge.expectMsg(RfBridge.Commands.SetPipeMatrix(matrix))
    rfBridge.reply(RfBridge.Responses.SuccessDriverOperation)
    probe.expectMsg(RfConfigurer.Responses.SuccessOperation)
  }

  "Return error if some pipe matrix addressed have error" in {
    val probe = TestProbe()
    val rfBridge = TestProbe()
    val config = RfConfiguration(
      "",
      0,
      0,
      "AAAAAA01",
      "AAAAAA11",
      "AAAAAA02",
      "AAAAAA12",
      "GAAAAA03",
      "AAAAAA13",
      "AAAAAA04",
      "AAAAAA14",
      "AAAAAA05",
      "AAAAAA15"
    )

    val target = system.actorOf(RfConfigurer.props(config, rfBridge.ref, testMode = true))
    probe.send(target, RfConfigurer.Commands.Exec())
    probe.expectMsg(RfConfigurer.Responses.FailedOperation)
  }

  "Return error if rf bridge respond with BadPipeMatrix" in {
    val probe = TestProbe()
    val rfBridge = TestProbe()
    val config = RfConfiguration(
      "",
      0,
      0,
      "AAAAAA01",
      "AAAAAA11",
      "AAAAAA02",
      "AAAAAA12",
      "AAAAAA03",
      "AAAAAA13",
      "AAAAAA04",
      "AAAAAA14",
      "AAAAAA05",
      "AAAAAA15"
    )

    val target = system.actorOf(RfConfigurer.props(config, rfBridge.ref, testMode = true))
    probe.send(target, RfConfigurer.Commands.Exec())
    rfBridge.expectMsgType[RfBridge.Commands.SetPipeMatrix]
    rfBridge.reply(RfBridge.Responses.BadPipeMatrix)
    probe.expectMsg(RfConfigurer.Responses.FailedOperation)
  }

  "Return error if rf bridge respond with BadPipeLsb" in {
    val probe = TestProbe()
    val rfBridge = TestProbe()
    val config = RfConfiguration(
      "",
      0,
      0,
      "AAAAAA01",
      "AAAAAA11",
      "AAAAAA02",
      "AAAAAA12",
      "AAAAAA03",
      "AAAAAA13",
      "AAAAAA04",
      "AAAAAA14",
      "AAAAAA05",
      "AAAAAA15"
    )

    val target = system.actorOf(RfConfigurer.props(config, rfBridge.ref, testMode = true))
    probe.send(target, RfConfigurer.Commands.Exec())
    rfBridge.expectMsgType[RfBridge.Commands.SetPipeMatrix]
    rfBridge.reply(RfBridge.Responses.BadPipeMsb)
    probe.expectMsg(RfConfigurer.Responses.FailedOperation)
  }

  "Return error if rf bridge respond with TransactionTimeout" in {
    val probe = TestProbe()
    val rfBridge = TestProbe()
    val config = RfConfiguration(
      "",
      0,
      0,
      "AAAAAA01",
      "AAAAAA11",
      "AAAAAA02",
      "AAAAAA12",
      "AAAAAA03",
      "AAAAAA13",
      "AAAAAA04",
      "AAAAAA14",
      "AAAAAA05",
      "AAAAAA15"
    )

    val target = system.actorOf(RfConfigurer.props(config, rfBridge.ref, testMode = true))
    probe.send(target, RfConfigurer.Commands.Exec())
    rfBridge.expectMsgType[RfBridge.Commands.SetPipeMatrix]
    rfBridge.reply(RfBridge.Responses.TransactionTimeout)
    probe.expectMsg(RfConfigurer.Responses.FailedOperation)
  }

  "Return error if rf bridge does not respond" in {
    val probe = TestProbe()
    val rfBridge = TestProbe()
    val config = RfConfiguration(
      "",
      0,
      0,
      "AAAAAA01",
      "AAAAAA11",
      "AAAAAA02",
      "AAAAAA12",
      "AAAAAA03",
      "AAAAAA13",
      "AAAAAA04",
      "AAAAAA14",
      "AAAAAA05",
      "AAAAAA15"
    )

    val target = system.actorOf(RfConfigurer.props(config, rfBridge.ref, testMode = true))
    probe.send(target, RfConfigurer.Commands.Exec())
    probe.expectMsg(RfConfigurer.Responses.FailedOperation)
  }

  "Return error if rf bridge respond with ChipNotRespond" in {
    val probe = TestProbe()
    val rfBridge = TestProbe()
    val config = RfConfiguration(
      "",
      0,
      0,
      "AAAAAA01",
      "AAAAAA11",
      "AAAAAA02",
      "AAAAAA12",
      "AAAAAA03",
      "AAAAAA13",
      "AAAAAA04",
      "AAAAAA14",
      "AAAAAA05",
      "AAAAAA15"
    )

    val target = system.actorOf(RfConfigurer.props(config, rfBridge.ref, testMode = true))
    probe.send(target, RfConfigurer.Commands.Exec())
    rfBridge.expectMsgType[RfBridge.Commands.SetPipeMatrix]
    rfBridge.reply(RfBridge.Responses.ChipNotRespond)
    probe.expectMsg(RfConfigurer.Responses.FailedOperation)
  }
}