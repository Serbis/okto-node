package ru.serbis.okto.node.unit.runtime.senv

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import akka.util.ByteString
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import ru.serbis.okto.node.log.{StdOutLogger, StreamLogger}
import ru.serbis.okto.node.runtime.senv.VStdIn
import ru.serbis.okto.node.runtime.{Stream, StreamControls}
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.{Await, Future}

class VStdInSpec extends TestKit(ActorSystem("TestSystem")) with ImplicitSender with WordSpecLike with Matchers with BeforeAndAfterAll with StreamLogger {

  override protected def beforeAll(): Unit = {
    initializeGlobalLogger (system)
    logger.addDestination (system.actorOf (StdOutLogger.props) )
  }

  "VStdIn" must {
    "For 'read' method" should {
      "Return received data" in {
        val executor = TestProbe()
        val stdIn = TestProbe()
        val target = new VStdIn(executor.ref, stdIn.ref)

        val rf = Future {
          target.read(5000)
        }
        stdIn.expectMsg(Stream.Commands.ReadOnReceive)
        stdIn.reply(Stream.Responses.Data(ByteString("abc") ++ ByteString(Array(StreamControls.EOI))))
        val result = Await.result(rf, 5 second)
        result shouldEqual "abc"
      }

      "Buffer data received before EOI (if eoi does not received in the current data pack) and return full data block after receive EOI" in {
        val executor = TestProbe()
        val stdIn = TestProbe()
        val target = new VStdIn(executor.ref, stdIn.ref)

        val rf = Future {
          target.read(5000)
        }
        stdIn.expectMsg(Stream.Commands.ReadOnReceive)
        stdIn.reply(Stream.Responses.Data(ByteString("abc")))
        stdIn.expectMsg(Stream.Commands.ReadOnReceive)
        stdIn.reply(Stream.Responses.Data(ByteString("d") ++ ByteString(Array(StreamControls.EOI))))
        val result = Await.result(rf, 5 second)
        result shouldEqual "abcd"
      }

      "Buffer data after EOI" in {
        val executor = TestProbe()
        val stdIn = TestProbe()
        val target = new VStdIn(executor.ref, stdIn.ref)

        val rf = Future {
          target.read(5000)
        }
        stdIn.expectMsg(Stream.Commands.ReadOnReceive)
        stdIn.reply(Stream.Responses.Data(ByteString("abc")))
        stdIn.expectMsg(Stream.Commands.ReadOnReceive)
        stdIn.reply(Stream.Responses.Data(ByteString("d") ++ ByteString(Array(StreamControls.EOI)) ++ ByteString("qwe")))
        val result = Await.result(rf, 5 second)
        result shouldEqual "abcd"

        val rf2 = Future {
          target.read(5000)
        }
        stdIn.expectMsg(Stream.Commands.ReadOnReceive)
        stdIn.reply(Stream.Responses.Data(ByteString(Array(StreamControls.EOI))))
        val result2 = Await.result(rf2, 5 second)
        result2 shouldEqual "qwe"
      }

      "Return null if data expectation timeout was reached (data shold be bufferd)" in {
        val executor = TestProbe()
        val probe = TestProbe()
        val stdIn = TestProbe()
        val target = new VStdIn(executor.ref, stdIn.ref)

        val rf = Future {
          target.read(2000)
        }
        stdIn.expectMsg(Stream.Commands.ReadOnReceive)
        stdIn.reply(Stream.Responses.Data(ByteString("abc")))
        probe.expectMsg(1000)
        stdIn.expectMsg(Stream.Commands.ReadOnReceive)
        stdIn.reply(Stream.Responses.Data(ByteString("abc")))
        probe.expectMsg(1000)
        val result = Await.result(rf, 5 second)
        result shouldEqual "null"

        val rf2 = Future {
          target.read(5000)
        }
        stdIn.expectMsg(Stream.Commands.ReadOnReceive)
        stdIn.reply(Stream.Responses.Data(ByteString(Array(StreamControls.EOI))))
        val result2 = Await.result(rf2, 5 second)
        result2 shouldEqual "abcabc"
      }

      "Return data if data already is buffered with EOF" in {
        fail
      }
    }
  }
}
