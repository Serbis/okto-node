package ru.serbis.okto.node.unit.hardware

import akka.actor.ActorSystem
import akka.stream._
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import akka.util.ByteString
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import ru.serbis.okto.node.events.{Eventer, HardwareEvent}
import ru.serbis.okto.node.hardware.packets.EventConfirmator
import ru.serbis.okto.node.log.Logger.LogLevels
import ru.serbis.okto.node.log.{StdOutLogger, StreamLogger}


class EventConfirmatorSpec extends TestKit(ActorSystem("TestSystem")) with ImplicitSender with WordSpecLike with Matchers with StreamLogger with BeforeAndAfterAll {

  implicit val mater = ActorMaterializer()

  override protected def beforeAll(): Unit = {
    initializeGlobalLogger(system, LogLevels.Info)
    logger.addDestination(system.actorOf(StdOutLogger.props, "StdOutLogger"))
  }

  "EventConfigurer" must {
    "Must process positive  for delivered scenario" in {
      val eventer = TestProbe()
      val probe = TestProbe()
      val event = HardwareEvent(99, 199, 299, confirmed = true, ByteString("abc"))

      val target = system.actorOf(EventConfirmator.props(eventer.ref, testMode = true))
      probe.send(target, EventConfirmator.Commands.Exec(event))
      eventer.expectMsg(Eventer.Commands.Receive(event))
      eventer.reply(Eventer.Responses.Delivered(event))
      probe.expectMsg(EventConfirmator.Responses.EventAck(199, 299, result = true))
    }

    "Must process positive for not delivered scenario" in {
      val eventer = TestProbe()
      val probe = TestProbe()
      val event = HardwareEvent(99, 199, 299, confirmed = true, ByteString("abc"))

      val target = system.actorOf(EventConfirmator.props(eventer.ref, testMode = true))
      probe.send(target, EventConfirmator.Commands.Exec(event))
      eventer.expectMsg(Eventer.Commands.Receive(event))
      eventer.reply(Eventer.Responses.NotDelivered(event, 0))
      probe.expectMsg(EventConfirmator.Responses.EventAck(199, 299, result = false))
    }

    "Return error if eventer does not respond" in {
      val eventer = TestProbe()
      val probe = TestProbe()
      val event = HardwareEvent(99, 199, 299, confirmed = true, ByteString("abc"))

      val target = system.actorOf(EventConfirmator.props(eventer.ref, testMode = true))
      probe.send(target, EventConfirmator.Commands.Exec(event))
      probe.expectMsg(EventConfirmator.Responses.EventAck(199, 299, result = false))
    }
  }




}