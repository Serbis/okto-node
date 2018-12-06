package ru.serbis.okto.node.unit.runtime.senv

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import ru.serbis.okto.node.hardware.SerialBridge
import ru.serbis.okto.node.log.{StdOutLogger, StreamLogger}
import ru.serbis.okto.node.runtime.AppCmdExecutor
import ru.serbis.okto.node.runtime.senv.{VBridge, VRuntime}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class VBridgeSpec extends TestKit(ActorSystem("TestSystem")) with ImplicitSender with WordSpecLike with Matchers with BeforeAndAfterAll with StreamLogger {

  override protected def beforeAll(): Unit = {
    initializeGlobalLogger(system)
    logger.addDestination(system.actorOf(StdOutLogger.props))
  }

  "VBridge" must {
    "Send request to the serial bridge and return response" in {
      val serialBridge = TestProbe()
      val target = new VBridge(serialBridge.ref)

      val shellFut = Future {
        target.req("a")
      }
      serialBridge.expectMsg(SerialBridge.Commands.SerialRequest("a", 543))
      serialBridge.reply(SerialBridge.Responses.SerialResponse("b", 543))
      val result = Await.result(shellFut, 1 second)
      result shouldEqual "b"
    }

    "Return null if serial bridge response timeout was reached" in {
      val serialBridge = TestProbe()
      val target = new VBridge(serialBridge.ref)

      val shellFut = Future {
        target.req("a")
      }

      val result = Await.result(shellFut, 5 second)
      result shouldEqual null
    }

    "Send null if serial bridge respond with ResponseTimeout" in {
      val serialBridge = TestProbe()
      val target = new VBridge(serialBridge.ref)

      val shellFut = Future {
        target.req("a")
      }
      serialBridge.expectMsg(SerialBridge.Commands.SerialRequest("a", 543))
      serialBridge.reply(SerialBridge.Responses.ResponseTimeout(543))
      val result = Await.result(shellFut, 1 second)
      result shouldEqual null
    }

    "Send null if serial bridge respond with HardwareError" in {
      val serialBridge = TestProbe()
      val target = new VBridge(serialBridge.ref)

      val shellFut = Future {
        target.req("a")
      }
      serialBridge.expectMsg(SerialBridge.Commands.SerialRequest("a", 543))
      serialBridge.reply(SerialBridge.Responses.HardwareError("x", 543))
      val result = Await.result(shellFut, 1 second)
      result shouldEqual null
    }

    "Send null if serial bridge respond with BridgeOverload" in {
      val serialBridge = TestProbe() //BridgeOverload
      val target = new VBridge(serialBridge.ref)

      val shellFut = Future {
        target.req("a")
      }
      serialBridge.expectMsg(SerialBridge.Commands.SerialRequest("a", 543))
      serialBridge.reply(SerialBridge.Responses.BridgeOverload(543))
      val result = Await.result(shellFut, 1 second)
      result shouldEqual null
    }

    "Send null if serial bridge respond with unexpected message" in {
      val serialBridge = TestProbe() //BridgeOverload
      val target = new VBridge(serialBridge.ref)

      val shellFut = Future {
        target.req("a")
      }
      serialBridge.expectMsg(SerialBridge.Commands.SerialRequest("a", 543))
      serialBridge.reply("e")
      val result = Await.result(shellFut, 1 second)
      result shouldEqual null
    }
  }
}