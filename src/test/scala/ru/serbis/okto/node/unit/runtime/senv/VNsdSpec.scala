package ru.serbis.okto.node.unit.runtime.senv

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import ru.serbis.okto.node.hardware.SystemDaemon
import ru.serbis.okto.node.log.{StdOutLogger, StreamLogger}
import ru.serbis.okto.node.runtime.senv.{VBridge, VNsd}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class VNsdSpec extends TestKit(ActorSystem("TestSystem")) with ImplicitSender with WordSpecLike with Matchers with BeforeAndAfterAll with StreamLogger {

  override protected def beforeAll(): Unit = {
    initializeGlobalLogger(system)
    logger.addDestination(system.actorOf(StdOutLogger.props))
  }

  "VNsd" must {
    "Send request to the NSD and return response" in {
      val systemDaemon = TestProbe()
      val target = new VNsd(systemDaemon.ref)

      val shellFut = Future {
        target.req("a")
      }
      systemDaemon.expectMsg(SystemDaemon.Commands.DaemonRequest("a", 134))
      systemDaemon.reply(SystemDaemon.Responses.DaemonResponse("b", 134))
      val result = Await.result(shellFut, 1 second)
      result shouldEqual "b"
    }

    "Return null if NSD response timeout was reached" in {
      val systemDaemon = TestProbe()
      val target = new VNsd(systemDaemon.ref)

      val shellFut = Future {
        target.req("a")
      }

      val result = Await.result(shellFut, 5 second)
      result shouldEqual null
    }

    "Send null if NSD respond with ResponseTimeout" in {
      val systemDaemon = TestProbe()
      val target = new VNsd(systemDaemon.ref)

      val shellFut = Future {
        target.req("a")
      }
      systemDaemon.expectMsg(SystemDaemon.Commands.DaemonRequest("a", 134))
      systemDaemon.reply(SystemDaemon.Responses.ResponseTimeout(134))
      val result = Await.result(shellFut, 1 second)
      result shouldEqual null
    }

    "Send null if NSD respond with HardwareError" in {
      val systemDaemon = TestProbe()
      val target = new VNsd(systemDaemon.ref)

      val shellFut = Future {
        target.req("a")
      }
      systemDaemon.expectMsg(SystemDaemon.Commands.DaemonRequest("a", 134))
      systemDaemon.reply(SystemDaemon.Responses.DaemonError("x", 134))
      val result = Await.result(shellFut, 1 second)
      result shouldEqual null
    }

    "Send null if serial bridge respond with BridgeOverload" in {
      val systemDaemon = TestProbe() //BridgeOverload
      val target = new VNsd(systemDaemon.ref)

      val shellFut = Future {
        target.req("a")
      }
      systemDaemon.expectMsg(SystemDaemon.Commands.DaemonRequest("a", 134))
      systemDaemon.reply(SystemDaemon.Responses.DaemonOverload(134))
      val result = Await.result(shellFut, 1 second)
      result shouldEqual null
    }

    "Send null if serial bridge respond with unexpected message" in {
      val systemDaemon = TestProbe() //BridgeOverload
      val target = new VNsd(systemDaemon.ref)

      val shellFut = Future {
        target.req("a")
      }
      systemDaemon.expectMsg(SystemDaemon.Commands.DaemonRequest("a", 134))
      systemDaemon.reply("e")
      val result = Await.result(shellFut, 1 second)
      result shouldEqual null
    }
  }
}