package ru.serbis.okto.node.unit.events

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import akka.util.ByteString
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import ru.serbis.okto.node.common.{CommandsUnion, Env}
import ru.serbis.okto.node.events.{Eventer, HardwareEvent}
import ru.serbis.okto.node.log.{StdOutLogger, StreamLogger}
import ru.serbis.okto.node.runtime.{Process, Runtime, Stream}
import scala.concurrent.duration._

import scala.collection.mutable
import scala.concurrent.Future

class EventerSpec extends TestKit(ActorSystem("TestSystem")) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll with StreamLogger {

  override protected def beforeAll(): Unit = {
    initializeGlobalLogger(system)
    logger.addDestination(system.actorOf(StdOutLogger.props))
  }

  "Eventer" must {
    "Transmit event for all followers" in {
      val probe = TestProbe()
      val f1 = TestProbe()
      val f2 = TestProbe()
      val f3 = TestProbe()
      val target = system.actorOf(Eventer.props())
      val event = HardwareEvent(1, 100, 99, confirmed = true, ByteString("x"))

      probe.send(target, Eventer.Commands.Subscribe(Some(1), f1.ref))
      probe.expectMsg(Eventer.Responses.Subscribed)
      probe.send(target, Eventer.Commands.Subscribe(Some(1), f2.ref))
      probe.expectMsg(Eventer.Responses.Subscribed)
      probe.send(target, Eventer.Commands.Subscribe(Some(1), f3.ref))
      probe.expectMsg(Eventer.Responses.Subscribed)

      probe.send(target, Eventer.Commands.Receive(event))

      f1.expectMsg(event)
      f2.expectMsg(event)
      f3.expectMsg(event)

      probe.expectMsg(Eventer.Responses.Delivered(event))
    }

    "Return delivered if not followers for the event" in {
      val probe = TestProbe()
      val target = system.actorOf(Eventer.props())
      val event = HardwareEvent(1, 100, 99, confirmed = true, ByteString("x"))

      probe.send(target, Eventer.Commands.Receive(event))
      probe.expectMsg(Eventer.Responses.Delivered(event))
    }

    "Subscribe actor for specified eid" in {
      val probe = TestProbe()
      val f1 = TestProbe()
      val target = system.actorOf(Eventer.props())
      val event = HardwareEvent(1, 100, 99, confirmed = true, ByteString("x"))

      probe.send(target, Eventer.Commands.Subscribe(Some(1), f1.ref))
      probe.expectMsg(Eventer.Responses.Subscribed)
      probe.send(target, Eventer.Commands.Receive(event))
      f1.expectMsg(event)
    }

    "Subscribe actor as watcher" in {
      val probe = TestProbe()
      val f1 = TestProbe()
      val target = system.actorOf(Eventer.props())
      val event1 = HardwareEvent(1, 100, 99, confirmed = true, ByteString("x"))
      val event2 = HardwareEvent(2, 100, 99, confirmed = true, ByteString("x"))
      val event3 = HardwareEvent(3, 100, 99, confirmed = true, ByteString("x"))

      probe.send(target, Eventer.Commands.Subscribe(None, f1.ref))
      probe.expectMsg(Eventer.Responses.Subscribed)

      probe.send(target, Eventer.Commands.Receive(event1))
      f1.expectMsg(event1)
      probe.expectMsg(Eventer.Responses.Delivered(event1))
      probe.send(target, Eventer.Commands.Receive(event2))
      f1.expectMsg(event2)
      probe.expectMsg(Eventer.Responses.Delivered(event2))
      probe.send(target, Eventer.Commands.Receive(event3))
      f1.expectMsg(event3)
      probe.expectMsg(Eventer.Responses.Delivered(event3))
    }

    "Unsubscribe actor for specified eid" in {
      val probe = TestProbe()
      val f1 = TestProbe()
      val target = system.actorOf(Eventer.props())
      val event1 = HardwareEvent(1, 100, 99, confirmed = true, ByteString("x"))
      val event2 = HardwareEvent(2, 100, 99, confirmed = true, ByteString("x"))
      val event3 = HardwareEvent(3, 100, 99, confirmed = true, ByteString("x"))

      probe.send(target, Eventer.Commands.Subscribe(Some(1), f1.ref))
      probe.expectMsg(Eventer.Responses.Subscribed)
      probe.send(target, Eventer.Commands.Subscribe(Some(2), f1.ref))
      probe.expectMsg(Eventer.Responses.Subscribed)
      probe.send(target, Eventer.Commands.Subscribe(Some(3), f1.ref))
      probe.expectMsg(Eventer.Responses.Subscribed)

      probe.send(target, Eventer.Commands.Receive(event1))
      f1.expectMsg(event1)
      probe.expectMsg(Eventer.Responses.Delivered(event1))
      probe.send(target, Eventer.Commands.Receive(event2))
      f1.expectMsg(event2)
      probe.expectMsg(Eventer.Responses.Delivered(event2))
      probe.send(target, Eventer.Commands.Receive(event3))
      f1.expectMsg(event3)
      probe.expectMsg(Eventer.Responses.Delivered(event3))

      probe.send(target, Eventer.Commands.Unsubscribe(Some(2), f1.ref))
      probe.expectMsg(Eventer.Responses.Unsubscribed)

      probe.send(target, Eventer.Commands.Receive(event1))
      f1.expectMsg(event1)
      probe.expectMsg(Eventer.Responses.Delivered(event1))
      probe.send(target, Eventer.Commands.Receive(event2))
      f1.expectNoMessage(0.5 second)
      probe.expectMsg(Eventer.Responses.Delivered(event2))
      probe.send(target, Eventer.Commands.Receive(event3))
      f1.expectMsg(event3)
      probe.expectMsg(Eventer.Responses.Delivered(event3))
    }

    "Unsubscribe actor for all eid's" in {
      val probe = TestProbe()
      val f1 = TestProbe()
      val target = system.actorOf(Eventer.props())
      val event1 = HardwareEvent(1, 100, 99, confirmed = true, ByteString("x"))
      val event2 = HardwareEvent(2, 100, 99, confirmed = true, ByteString("x"))
      val event3 = HardwareEvent(3, 100, 99, confirmed = true, ByteString("x"))


      //Test for followers
      probe.send(target, Eventer.Commands.Subscribe(Some(1), f1.ref))
      probe.expectMsg(Eventer.Responses.Subscribed)
      probe.send(target, Eventer.Commands.Subscribe(Some(2), f1.ref))
      probe.expectMsg(Eventer.Responses.Subscribed)
      probe.send(target, Eventer.Commands.Subscribe(Some(3), f1.ref))
      probe.expectMsg(Eventer.Responses.Subscribed)

      probe.send(target, Eventer.Commands.Receive(event1))
      f1.expectMsg(event1)
      probe.expectMsg(Eventer.Responses.Delivered(event1))
      probe.send(target, Eventer.Commands.Receive(event2))
      f1.expectMsg(event2)
      probe.expectMsg(Eventer.Responses.Delivered(event2))
      probe.send(target, Eventer.Commands.Receive(event3))
      f1.expectMsg(event3)
      probe.expectMsg(Eventer.Responses.Delivered(event3))

      probe.send(target, Eventer.Commands.Unsubscribe(None, f1.ref))
      probe.expectMsg(Eventer.Responses.Unsubscribed)

      probe.send(target, Eventer.Commands.Receive(event1))
      f1.expectNoMessage(0.5 second)
      probe.expectMsg(Eventer.Responses.Delivered(event1))
      probe.send(target, Eventer.Commands.Receive(event2))
      f1.expectNoMessage(0.5 second)
      probe.expectMsg(Eventer.Responses.Delivered(event2))
      probe.send(target, Eventer.Commands.Receive(event3))
      f1.expectNoMessage(0.5 second)
      probe.expectMsg(Eventer.Responses.Delivered(event3))

      //Test for watchers
      probe.send(target, Eventer.Commands.Subscribe(None, f1.ref))
      probe.expectMsg(Eventer.Responses.Subscribed)

      probe.send(target, Eventer.Commands.Receive(event1))
      f1.expectMsg(event1)
      probe.expectMsg(Eventer.Responses.Delivered(event1))
      probe.send(target, Eventer.Commands.Receive(event2))
      f1.expectMsg(event2)
      probe.expectMsg(Eventer.Responses.Delivered(event2))
      probe.send(target, Eventer.Commands.Receive(event3))
      f1.expectMsg(event3)
      probe.expectMsg(Eventer.Responses.Delivered(event3))

      probe.send(target, Eventer.Commands.Unsubscribe(None, f1.ref))
      probe.expectMsg(Eventer.Responses.Unsubscribed)

      probe.send(target, Eventer.Commands.Receive(event1))
      f1.expectNoMessage(0.5 second)
      probe.expectMsg(Eventer.Responses.Delivered(event1))
      probe.send(target, Eventer.Commands.Receive(event2))
      f1.expectNoMessage(0.5 second)
      probe.expectMsg(Eventer.Responses.Delivered(event2))
      probe.send(target, Eventer.Commands.Receive(event3))
      f1.expectNoMessage(0.5 second)
      probe.expectMsg(Eventer.Responses.Delivered(event3))

    }
  }

}
