package ru.serbis.okto.node.unit.runtime.senv

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import ru.serbis.okto.node.hardware.{RfBridge, SerialBridge}
import ru.serbis.okto.node.log.{StdOutLogger, StreamLogger}
import ru.serbis.okto.node.runtime.app.AppCmdExecutor
import ru.serbis.okto.node.runtime.senv.VBridge

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

class VBridgeSpec extends TestKit(ActorSystem("TestSystem")) with ImplicitSender with WordSpecLike with Matchers with BeforeAndAfterAll with StreamLogger {

  override protected def beforeAll(): Unit = {
    initializeGlobalLogger(system)
    logger.addDestination(system.actorOf(StdOutLogger.props))
  }

  "VBridge" must {
    "Send requests to the bridges and return correct responses" in {
      val serialBridge = TestProbe()
      val rfBridge = TestProbe()
      val target = new VBridge(serialBridge.ref, rfBridge.ref)

      var shellFut = Future {
        target.req(0, "a")
      }
      serialBridge.expectMsg(SerialBridge.Commands.ExbCommand("a", 3000))
      serialBridge.reply(SerialBridge.Responses.ExbResponse("b"))
      var result = Await.result(shellFut, 1 second)
      result.error shouldEqual 0
      result.result shouldEqual "b"


      shellFut = Future {
        target.req(0, "a")
      }
      serialBridge.expectMsg(SerialBridge.Commands.ExbCommand("a", 3000))
      serialBridge.reply(SerialBridge.Responses.ExbError(0, "e"))
      result = Await.result(shellFut, 1 second)
      result.error shouldEqual 1
      result.result shouldEqual "0/e"


      shellFut = Future {
        target.req(0, "a")
      }
      serialBridge.expectMsg(SerialBridge.Commands.ExbCommand("a", 3000))
      serialBridge.reply(SerialBridge.Responses.BridgeOverload)
      result = Await.result(shellFut, 1 second)
      result.error shouldEqual 4
      result.result shouldEqual ""


      shellFut = Future {
        target.req(0, "a")
      }
      serialBridge.expectMsg(SerialBridge.Commands.ExbCommand("a", 3000))
      serialBridge.reply(SerialBridge.Responses.TransactionTimeout)
      result = Await.result(shellFut, 1 second)
      result.error shouldEqual 6
      result.result shouldEqual ""


      shellFut = Future {
        target.req(0, "a")
      }
      serialBridge.expectMsg(SerialBridge.Commands.ExbCommand("a", 3000))
      serialBridge.reply("x")
      result = Await.result(shellFut, 1 second)
      result.error shouldEqual 8
      result.result shouldEqual ""

      //------------------------------------------------------------------

      shellFut = Future {
        target.req(1, "a")
      }
      rfBridge.expectMsg(RfBridge.Commands.ExbCommand(1, "a", 3000))
      rfBridge.reply(RfBridge.Responses.ExbResponse("b"))
      result = Await.result(shellFut, 1 second)
      result.error shouldEqual 0
      result.result shouldEqual "b"


      shellFut = Future {
        target.req(1, "a")
      }
      rfBridge.expectMsg(RfBridge.Commands.ExbCommand(1, "a", 3000))
      rfBridge.reply(RfBridge.Responses.ExbError(0, "e"))
      result = Await.result(shellFut, 1 second)
      result.error shouldEqual 1
      result.result shouldEqual "0/e"


      shellFut = Future {
        target.req(1, "a")
      }
      rfBridge.expectMsg(RfBridge.Commands.ExbCommand(1, "a", 3000))
      rfBridge.reply(RfBridge.Responses.ExbAddrNotDefined)
      result = Await.result(shellFut, 1 second)
      result.error shouldEqual 2
      result.result shouldEqual ""


      shellFut = Future {
        target.req(1, "a")
      }
      rfBridge.expectMsg(RfBridge.Commands.ExbCommand(1, "a", 3000))
      rfBridge.reply(RfBridge.Responses.ExbUnreachable)
      result = Await.result(shellFut, 1 second)
      result.error shouldEqual 3
      result.result shouldEqual ""


      shellFut = Future {
        target.req(1, "a")
      }
      rfBridge.expectMsg(RfBridge.Commands.ExbCommand(1, "a", 3000))
      rfBridge.reply(RfBridge.Responses.BridgeOverload)
      result = Await.result(shellFut, 1 second)
      result.error shouldEqual 4
      result.result shouldEqual ""


      shellFut = Future {
        target.req(1, "a")
      }
      rfBridge.expectMsg(RfBridge.Commands.ExbCommand(1, "a", 3000))
      rfBridge.reply(RfBridge.Responses.ExbBrokenResponse)
      result = Await.result(shellFut, 1 second)
      result.error shouldEqual 5
      result.result shouldEqual ""


      shellFut = Future {
        target.req(1, "a")
      }
      rfBridge.expectMsg(RfBridge.Commands.ExbCommand(1, "a", 3000))
      rfBridge.reply(RfBridge.Responses.TransactionTimeout)
      result = Await.result(shellFut, 1 second)
      result.error shouldEqual 6
      result.result shouldEqual ""


      shellFut = Future {
        target.req(1, "a")
      }
      rfBridge.expectMsg(RfBridge.Commands.ExbCommand(1, "a", 3000))
      rfBridge.reply(RfBridge.Responses.DriverError)
      result = Await.result(shellFut, 1 second)
      result.error shouldEqual 7
      result.result shouldEqual ""


      shellFut = Future {
        target.req(1, "a")
      }
      rfBridge.expectMsg(RfBridge.Commands.ExbCommand(1, "a", 3000))
      rfBridge.reply("x")
      result = Await.result(shellFut, 1 second)
      result.error shouldEqual 8
      result.result shouldEqual ""
    }
  }
}