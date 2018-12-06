package ru.serbis.okto.node.unit.runtime.senv

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import akka.util.ByteString
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import ru.serbis.okto.node.log.{StdOutLogger, StreamLogger}
import ru.serbis.okto.node.runtime.{Stream, StreamControls}
import ru.serbis.okto.node.runtime.senv.VShellLocal
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global

import scala.concurrent.{Await, Future}

class VShellLocalSpec extends TestKit(ActorSystem("TestSystem")) with ImplicitSender with WordSpecLike with Matchers with BeforeAndAfterAll with StreamLogger {

  override protected def beforeAll(): Unit = {
    initializeGlobalLogger (system)
    logger.addDestination (system.actorOf (StdOutLogger.props) )
  }

  "VShellLocal" must {
    "For 'read' method" should {
      "Return received data" in {
        val shellProcess = TestProbe()
        val shellStdIn = TestProbe()
        val shellStdOut = TestProbe()
        val target = new VShellLocal(shellProcess.ref, shellStdIn.ref, shellStdOut.ref)

        val rf = Future { target.read(5000) }
        shellStdOut.expectMsg(Stream.Commands.ReadOnReceive)
        shellStdOut.reply(Stream.Responses.Data(ByteString("abc") ++ ByteString(Array(StreamControls.EOI))))
        val result = Await.result(rf, 5 second)
        result shouldEqual "abc"
      }

      "Buffer data received before EOI (if eoi does not received in the current data pack) and return full data block after receive EOI" in {
        val shellProcess = TestProbe()
        val shellStdIn = TestProbe()
        val shellStdOut = TestProbe()
        val target = new VShellLocal(shellProcess.ref, shellStdIn.ref, shellStdOut.ref)

        val rf = Future { target.read(5000) }
        shellStdOut.expectMsg(Stream.Commands.ReadOnReceive)
        shellStdOut.reply(Stream.Responses.Data(ByteString("abc")))
        shellStdOut.expectMsg(Stream.Commands.ReadOnReceive)
        shellStdOut.reply(Stream.Responses.Data(ByteString("d") ++ ByteString(Array(StreamControls.EOI))))
        val result = Await.result(rf, 5 second)
        result shouldEqual "abcd"
      }

      "Buffer data after EOI" in {
        val shellProcess = TestProbe()
        val shellStdIn = TestProbe()
        val shellStdOut = TestProbe()
        val target = new VShellLocal(shellProcess.ref, shellStdIn.ref, shellStdOut.ref)

        val rf = Future { target.read(5000) }
        shellStdOut.expectMsg(Stream.Commands.ReadOnReceive)
        shellStdOut.reply(Stream.Responses.Data(ByteString("abc")))
        shellStdOut.expectMsg(Stream.Commands.ReadOnReceive)
        shellStdOut.reply(Stream.Responses.Data(ByteString("d") ++ ByteString(Array(StreamControls.EOI)) ++ ByteString("qwe")))
        val result = Await.result(rf, 5 second)
        result shouldEqual "abcd"

        val rf2 = Future { target.read(5000) }
        shellStdOut.expectMsg(Stream.Commands.ReadOnReceive)
        shellStdOut.reply(Stream.Responses.Data(ByteString(Array(StreamControls.EOI))))
        val result2 = Await.result(rf2, 5 second)
        result2 shouldEqual "qwe"
      }

      "Return null if data expectation timeout was reached (data shold be bufferd)" in {
        val probe = TestProbe()
        val shellProcess = TestProbe()
        val shellStdIn = TestProbe()
        val shellStdOut = TestProbe()
        val target = new VShellLocal(shellProcess.ref, shellStdIn.ref, shellStdOut.ref)

        val rf = Future { target.read(2000) }
        shellStdOut.expectMsg(Stream.Commands.ReadOnReceive)
        shellStdOut.reply(Stream.Responses.Data(ByteString("abc")))
        probe.expectMsg(1000)
        shellStdOut.expectMsg(Stream.Commands.ReadOnReceive)
        shellStdOut.reply(Stream.Responses.Data(ByteString("abc")))
        probe.expectMsg(1000)
        val result = Await.result(rf, 5 second)
        result shouldEqual "null"

        val rf2 = Future { target.read(5000) }
        shellStdOut.expectMsg(Stream.Commands.ReadOnReceive)
        shellStdOut.reply(Stream.Responses.Data(ByteString(Array(StreamControls.EOI))))
        val result2 = Await.result(rf2, 5 second)
        result2 shouldEqual "abcabc"
      }

      "Return data if data already is buffered with EOF" in {
        fail
      }
    }

    "For 'expectPrompt' method" should {
      "Return true after receive prompt from shell stdOut stream" in {
        val shellProcess = TestProbe()
        val shellStdIn = TestProbe()
        val shellStdOut = TestProbe()
        val target = new VShellLocal(shellProcess.ref, shellStdIn.ref, shellStdOut.ref)

        val rf = Future { target.expectPrompt(5000) }
        shellStdOut.expectMsg(Stream.Commands.ReadOnReceive)
        shellStdOut.reply(Stream.Responses.Data(ByteString(Array(StreamControls.PROMPT))))
        val result = Await.result(rf, 5 second)
        result shouldEqual true
      }

      "Write data to the buffer in back of prompt (front is break because before prompt there can be no data)" in {
        val shellProcess = TestProbe()
        val shellStdIn = TestProbe()
        val shellStdOut = TestProbe()
        val target = new VShellLocal(shellProcess.ref, shellStdIn.ref, shellStdOut.ref)

        val rf = Future { target.expectPrompt(5000) }
        shellStdOut.expectMsg(Stream.Commands.ReadOnReceive)
        shellStdOut.reply(Stream.Responses.Data(ByteString(Array(StreamControls.PROMPT)) ++ ByteString("123")))
        val result = Await.result(rf, 5 second)
        result shouldEqual true

        val rf2 = Future { target.read(5000) }
        shellStdOut.expectMsg(Stream.Commands.ReadOnReceive)
        shellStdOut.reply(Stream.Responses.Data(ByteString(Array(StreamControls.EOI))))
        val result2 = Await.result(rf2, 5 second)
        result2 shouldEqual "123"

      }

      "Return false if prompt timeout was reached" in {
        val shellProcess = TestProbe()
        val shellStdIn = TestProbe()
        val shellStdOut = TestProbe()
        val target = new VShellLocal(shellProcess.ref, shellStdIn.ref, shellStdOut.ref)

        val rf = Future { target.expectPrompt(500) }
        val result = Await.result(rf, 5 second)
        result shouldEqual false
      }
    }

    "For 'expectEof' method" should {
      "Return true after receive eof from shell stdOut stream" in {
        val shellProcess = TestProbe()
        val shellStdIn = TestProbe()
        val shellStdOut = TestProbe()
        val target = new VShellLocal(shellProcess.ref, shellStdIn.ref, shellStdOut.ref)

        val rf = Future {
          target.expectEof(5000)
        }
        shellStdOut.expectMsg(Stream.Commands.ReadOnReceive)
        shellStdOut.reply(Stream.Responses.Data(ByteString(Array(StreamControls.EOF))))
        val result = Await.result(rf, 5 second)
        result shouldEqual true
      }

      "Write data to the buffer in back of eof (front is break)" in {
        val shellProcess = TestProbe()
        val shellStdIn = TestProbe()
        val shellStdOut = TestProbe()
        val target = new VShellLocal(shellProcess.ref, shellStdIn.ref, shellStdOut.ref)

        val rf = Future {
          target.expectEof(5000)
        }
        shellStdOut.expectMsg(Stream.Commands.ReadOnReceive)
        shellStdOut.reply(Stream.Responses.Data(ByteString(Array(StreamControls.EOF)) ++ ByteString("123")))
        val result = Await.result(rf, 5 second)
        result shouldEqual true

        val rf2 = Future {
          target.read(5000)
        }
        shellStdOut.expectMsg(Stream.Commands.ReadOnReceive)
        shellStdOut.reply(Stream.Responses.Data(ByteString(Array(StreamControls.EOI))))
        val result2 = Await.result(rf2, 5 second)
        result2 shouldEqual "123"
      }

      "Return false if eof timeout was reached" in {
        val shellProcess = TestProbe()
        val shellStdIn = TestProbe()
        val shellStdOut = TestProbe()
        val target = new VShellLocal(shellProcess.ref, shellStdIn.ref, shellStdOut.ref)

        val rf = Future {
          target.expectEof(500)
        }
        val result = Await.result(rf, 5 second)
        result shouldEqual false
      }
    }

    "For 'write' method" should {
      "Write data to the shell stdOut with EOF terminator and return true" in {
        val shellProcess = TestProbe()
        val shellStdIn = TestProbe()
        val shellStdOut = TestProbe()
        val target = new VShellLocal(shellProcess.ref, shellStdIn.ref, shellStdOut.ref)

        val rf = Future { target.write("abc") }
        shellStdIn.expectMsg(Stream.Commands.Write( ByteString("abc") ++ ByteString(Array(StreamControls.EOI))))
        shellStdIn.reply(Stream.Responses.Written)
        val result = Await.result(rf, 5 second)
        result shouldEqual true
      }

      "Return false if stdOut is overflowed" in {
        val shellProcess = TestProbe()
        val shellStdIn = TestProbe()
        val shellStdOut = TestProbe()
        val target = new VShellLocal(shellProcess.ref, shellStdIn.ref, shellStdOut.ref)

        val rf = Future { target.write("abc") }
        shellStdIn.expectMsg(Stream.Commands.Write( ByteString("abc") ++ ByteString(Array(StreamControls.EOI))))
        shellStdIn.reply(Stream.Responses.BufferIsFull)
        val result = Await.result(rf, 5 second)
        result shouldEqual false
      }
    }
  }
}
