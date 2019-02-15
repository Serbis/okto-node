package ru.serbis.okto.node.unit.runtime.senv

import akka.actor.ActorSystem
import akka.http.scaladsl.model._
import akka.http.scaladsl.model.headers.RawHeader
import akka.stream.ActorMaterializer
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import akka.util.ByteString
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import ru.serbis.okto.node.events.{Eventer, HardwareEvent}
import ru.serbis.okto.node.log.{StdOutLogger, StreamLogger}
import ru.serbis.okto.node.proxy.http.TestHttpProxy
import ru.serbis.okto.node.runtime.senv.{EventWrapper, HardwareEventWrapper, VEvents}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future, Promise}

class VEventsSpec extends TestKit(ActorSystem("TestSystem")) with ImplicitSender with WordSpecLike with Matchers with BeforeAndAfterAll with StreamLogger {

  override protected def beforeAll(): Unit = {
    initializeGlobalLogger(system)
    logger.addDestination(system.actorOf(StdOutLogger.props))
  }

  "VEvents" must {
    "Process typical work cycle" in {
      val executor = TestProbe()
      val eventer = TestProbe()
      val target = new VEvents(eventer.ref, executor.ref)

      //Subscribe for hardware events
      val f1 = Future { target.subHardEvent(1, 2) }
      eventer.expectMsg(Eventer.Commands.Subscribe(Some(1), executor.ref))
      eventer.reply(Eventer.Responses.Subscribed)
      Await.result(f1, 1 second) shouldEqual true

      //Ignore double subscribing for one eid
      val f2 = Future { target.subHardEvent(1, 3) }
      eventer.expectNoMessage(0.5 second)
      Await.result(f2, 1 second) shouldEqual true

      //Non blocking hardware event receive
      val event1 = HardwareEvent(1, 99,  2, confirmed = false, ByteString("abc"))
      target.__putEvent(event1)
      target.receive(false) shouldEqual EventWrapper(0, HardwareEventWrapper(1, 2, "abc"))

      //Blocking hardware event receive
      val event2 = HardwareEvent(1, 99,  3, confirmed = false, ByteString("abc"))
      val f3 = Future { target.receive(true) }
      Thread.sleep(500)
      f3.isCompleted shouldEqual false
      target.__putEvent(event2)
      Await.result(f3, 1 second) shouldEqual EventWrapper(0, HardwareEventWrapper(1, 3, "abc"))

      //Blocking hardware event receive with signal interrupt
      val p = Promise[EventWrapper]
      val t = new Thread(() => p.success(target.receive(true)))
      t.start()
      Thread.sleep(200)
      t.interrupt()
      val r = Await.result(p.future, 1 second)
      r shouldEqual null

      //Filtering of hardware events
      val event3 = HardwareEvent(1, 99,  5, confirmed = false, ByteString("abc"))
      target.__putEvent(event3)
      target.receive(false) shouldEqual null

      //Ignore unsubscribe from hardware events for existing filter pairs
      val f4 = Future { target.unsubHardEvent(1, 3) }
      eventer.expectNoMessage(0.5 second)
      Await.result(f4, 1 second) shouldEqual true

      //Check previous action
      target.__putEvent(event2)
      target.receive(false) shouldEqual null

      //Unsubscribe from hardware events if no filter pairs
      val f5 = Future { target.unsubHardEvent(1, 2) }
      eventer.expectMsg(Eventer.Commands.Unsubscribe(Some(1), executor.ref))
      eventer.reply(Eventer.Responses.Unsubscribed)
      Await.result(f5, 1 second) shouldEqual true
    }
  }
}