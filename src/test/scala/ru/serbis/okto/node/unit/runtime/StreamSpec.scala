package ru.serbis.okto.node.unit.runtime

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import akka.util.ByteString
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import ru.serbis.okto.node.log.{StdOutLogger, StreamLogger}
import ru.serbis.okto.node.runtime.Stream
import ru.serbis.okto.node.common.ReachTypes.ReachByteString
import ru.serbis.okto.node.runtime.Stream.Commands._
import ru.serbis.okto.node.runtime.Stream.Responses._

import scala.concurrent.duration._

class StreamSpec extends TestKit(ActorSystem("TestSystem")) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll with StreamLogger {

  override protected def beforeAll(): Unit = {
    initializeGlobalLogger(system)
    logger.addDestination(system.actorOf(StdOutLogger.props))
  }

  "Stream" must {
    "Write and Read data without attached actors" in {
      val probe = TestProbe()
      val target = system.actorOf(Stream.props(3, 0))
      probe.send(target, Write(ByteString("abc")))
      probe.expectMsg(Written)
      probe.send(target, Read(1))
      probe.expectMsg(Data(ByteString("a")))
      probe.send(target, Read(10))
      probe.expectMsg(Data(ByteString("bc")))
      probe.send(target, Read(10))
      probe.expectMsg(Data(ByteString()))

      probe.send(target, Write(ByteString("abc")))
      probe.expectMsg(Written)
      probe.send(target, Read(1))
      probe.expectMsg(Data(ByteString("a")))
      probe.send(target, Read(10))
      probe.expectMsg(Data(ByteString("bc")))
      probe.send(target, Read(10))
      probe.expectMsg(Data(ByteString()))
    }

    "For ReadOnReceive message" should {
      "Immediately return buffered data" in {
        val probe = TestProbe()
        val target = system.actorOf(Stream.props(3, 0))
        probe.send(target, Write(ByteString("abc")))
        probe.expectMsg(Written)
        probe.send(target, ReadOnReceive)
        probe.expectMsg(Data(ByteString("abc")))
      }

      "Wait incoming data and return it to the requester without buffering" in {
        val probe = TestProbe()
        val probe2 = TestProbe()
        val target = system.actorOf(Stream.props(3, 0))
        probe.send(target, ReadOnReceive)
        probe2.send(target, Write(ByteString("abc")))
        probe2.expectMsg(Written)
        probe.expectMsg(Data(ByteString("abc")))

      }
    }

    "Write and Read data as wrapped data without attached actors" in {
      val probe = TestProbe()
      val target = system.actorOf(Stream.props(3, 0))
      probe.send(target, WriteWrapped(ByteString("abc")))
      probe.expectMsg(Written)
      probe.send(target, Read(1))
      probe.expectMsg(Data(ByteString("a")))
      probe.send(target, Read(10))
      probe.expectMsg(Data(ByteString("bc")))
      probe.send(target, Read(10))
      probe.expectMsg(Data(ByteString()))

      probe.send(target, WriteWrapped(ByteString("abc")))
      probe.expectMsg(Written)
      probe.send(target, Read(1))
      probe.expectMsg(Data(ByteString("a")))
      probe.send(target, Read(10))
      probe.expectMsg(Data(ByteString("bc")))
      probe.send(target, Read(10))
      probe.expectMsg(Data(ByteString()))
    }

    "Return BufferIsFull if the recorded data does not fit into the buffer" in {
      val probe = TestProbe()
      val target = system.actorOf(Stream.props(3, 0))
      probe.send(target, Write(ByteString("ab")))
      probe.expectMsg(Written)
      probe.send(target, Write(ByteString("cd")))
      probe.expectMsg(BufferIsFull)
      probe.send(target, Write(ByteString("c")))
      probe.expectMsg(Written)
      probe.send(target, Write(ByteString("e")))
      probe.expectMsg(BufferIsFull)
    }


    "Attach actor, send data to it by Write message and Detach it" in {
      val probe = TestProbe()
      val receiver1 = TestProbe()
      val receiver2 = TestProbe()
      val target = system.actorOf(Stream.props(3, 0))
      probe.send(target, Stream.Commands.Attach(receiver1.ref))
      probe.expectMsg(Stream.Responses.Attached)
      probe.send(target, Stream.Commands.Attach(receiver2.ref))
      probe.expectMsg(Stream.Responses.Attached)
      probe.send(target, Write(ByteString("ab")))
      probe.expectMsg(Written)
      receiver1.expectMsg(Data(ByteString("ab")))
      receiver2.expectMsg(Data(ByteString("ab")))
      probe.send(target, Read(10))
      probe.expectMsg(Data(ByteString()))
      probe.send(target, Stream.Commands.Detach(receiver1.ref))
      probe.expectMsg(Stream.Responses.Detached)
      probe.send(target, Write(ByteString("ab")))
      probe.expectMsg(Written)
      receiver1.expectNoMessage(1 second)
      receiver2.expectMsg(Data(ByteString("ab")))
      probe.send(target, Read(10))
      probe.expectMsg(Data(ByteString()))
      probe.send(target, Stream.Commands.Detach(receiver2.ref))
      probe.expectMsg(Stream.Responses.Detached)
      probe.send(target, Write(ByteString("ab")))
      probe.expectMsg(Written)
      probe.send(target, Read(10))
      probe.expectMsg(Data(ByteString("ab")))
      probe.send(target, Read(10))
      probe.expectMsg(Data(ByteString()))
    }

    "Return pid by GetPid request" in {
      val probe = TestProbe()
      val target = system.actorOf(Stream.props(3, 1000))
      probe.send(target, Stream.Commands.GetPid)
      probe.expectMsg(Stream.Responses.Pid(1000))
    }

    "For Close messaged send Closed to all receivers and respond with this message to the sender" in {
      val probe = TestProbe()
      val receiver1 = TestProbe()
      val receiver2 = TestProbe()
      val target = system.actorOf(Stream.props(3, 0))
      probe.send(target, Stream.Commands.Attach(receiver1.ref))
      probe.expectMsg(Stream.Responses.Attached)
      probe.send(target, Stream.Commands.Attach(receiver2.ref))
      probe.expectMsg(Stream.Responses.Attached)
      probe.send(target, Stream.Commands.Close)
      receiver1.expectMsg(Closed)
      receiver2.expectMsg(Closed)
      probe.expectMsg(Closed)
    }

    "Flush buffer to consumers by Flush message" in {
      val probe = TestProbe()
      val receiver1 = TestProbe()
      val receiver2 = TestProbe()
      val target = system.actorOf(Stream.props(3, 0))
      probe.send(target, Write(ByteString("abc")))
      probe.expectMsg(Stream.Responses.Written)
      probe.send(target, Stream.Commands.Attach(receiver1.ref))
      probe.expectMsg(Stream.Responses.Attached)
      probe.send(target, Stream.Commands.Attach(receiver2.ref))
      probe.expectMsg(Stream.Responses.Attached)
      probe.send(target, Stream.Commands.Flush)
      receiver1.expectMsg(Data(ByteString("abc")))
      receiver2.expectMsg(Data(ByteString("abc")))
    }
  }
}
