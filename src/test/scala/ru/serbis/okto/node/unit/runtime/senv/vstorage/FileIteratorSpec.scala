package ru.serbis.okto.node.unit.runtime.senv.vstorage

import java.nio.channels.FileChannel
import java.nio.file.{Files, NoSuchFileException, StandardOpenOption}

import akka.Done
import akka.actor.{ActorSystem, Status}
import akka.stream._
import akka.stream.scaladsl.{Sink, Source}
import akka.stream.stage.{GraphStageLogic, GraphStageWithMaterializedValue, OutHandler}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import akka.util.ByteString
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import ru.serbis.okto.node.log.{StdOutLogger, StreamLogger}
import ru.serbis.okto.node.proxy.system.TestActorSystemProxy
import ru.serbis.okto.node.runtime.senv.vstorage.{IoCtl, StreamIterator}

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future, Promise}
import scala.util.{Failure, Success}

class FileIteratorSpec extends TestKit(ActorSystem("TestSystem")) with ImplicitSender with WordSpecLike with Matchers with BeforeAndAfterAll with StreamLogger {

  implicit val mat = ActorMaterializer.create(system)

  override protected def beforeAll(): Unit = {
    initializeGlobalLogger(system)
    logger.addDestination(system.actorOf(StdOutLogger.props))
  }

  //Actually this file process tests for FileIterator class, but because it is abstract, for tests uses StreamIterator class, that invoke all
  //her superclass logic

  "Stream iterator must" must {
    "Process positive test" in {
        val systemProbe = TestProbe()
        val data = ByteString("").toList
        val source = Source.fromGraph(new FileSourceStub(data))
        val ioCtl = TestProbe()

        val cf = Future { StreamIterator(source, new TestActorSystemProxy(systemProbe.ref, system)) }
        val props = systemProbe.expectMsgType[TestActorSystemProxy.Actions.ActorOf]
        props.props.actorClass() + "$" shouldEqual IoCtl.getClass.toString
        systemProbe.reply(ioCtl.ref)
        ioCtl.expectMsgPF() {
          case IoCtl.Commands.Pull => ioCtl.reply(ioCtl.reply(IoCtl.Responses.Chunk(Some(ByteString("a")))))
          case _ =>
        }
        ioCtl.expectMsgPF() {
          case IoCtl.Commands.Pull => ioCtl.reply(ioCtl.reply(IoCtl.Responses.Chunk(Some(ByteString("a")))))
          case _ =>
        }
        val target = Await.result(cf, 1 second)

        val ht1 = Future { target.hasNext() }
        Await.result(ht1, 1 second) shouldEqual true

        val n1 = Future { target.next() }
        ioCtl.expectMsg(IoCtl.Commands.Pull)
        ioCtl.reply(IoCtl.Responses.Chunk(Some(ByteString("b"))))
        Await.result(n1, 1 second) shouldEqual "a"


        val ht2 = Future { target.hasNext() }
        Await.result(ht2, 1 second) shouldEqual true

        val n2 = Future { target.next() }
        ioCtl.expectMsg(IoCtl.Commands.Pull)
        ioCtl.reply(IoCtl.Responses.Chunk(Some(ByteString("c"))))
        Await.result(n2, 1 second) shouldEqual "b"


        val ht3 = Future { target.hasNext() }
        Await.result(ht3, 1 second) shouldEqual true

        val n3 = Future { target.next() }
        ioCtl.expectMsg(IoCtl.Commands.Pull)
        ioCtl.reply(IoCtl.Responses.Chunk(None))
        Await.result(n3, 1 second) shouldEqual "c"

        val ht4 = Future { target.hasNext() }
        Await.result(ht4, 1 second) shouldEqual false

        val n4 = Future { target.next() }
        Await.result(n4, 1 second) shouldEqual null
    }

    "Fail iterator construction if stream competes with error" in {
        val systemProbe = TestProbe()
        val data = ByteString("").toList
        val source = Source.fromGraph(new FileSourceStub(data, 0))
        val ioCtl = TestProbe()

        val cf = Future {
          try {
            StreamIterator(source, new TestActorSystemProxy(systemProbe.ref, system))
            false
          } catch {
            case _: Throwable => true
          }
        }
        val props = systemProbe.expectMsgType[TestActorSystemProxy.Actions.ActorOf]
        props.props.actorClass() + "$" shouldEqual IoCtl.getClass.toString
        systemProbe.reply(ioCtl.ref)
        ioCtl.expectMsgPF() {
          case IoCtl.Commands.Pull => ioCtl.reply(IoCtl.Responses.StreamFailed(new Throwable("x")))
          case _ =>
        }
        ioCtl.expectMsgPF() {
          case IoCtl.Commands.Pull => ioCtl.reply(IoCtl.Responses.StreamFailed(new Throwable("x")))
          case _ =>
        }
        ioCtl.expectMsgPF() {
          case IoCtl.Commands.Pull => ioCtl.reply(IoCtl.Responses.StreamFailed(new Throwable("x")))
          case _ =>
        }

        Await.result(cf, 1 second) shouldEqual true
    }

    "Return no more elements if stream file at process" in {
        val systemProbe = TestProbe()
        val data = ByteString("").toList
        val source = Source.fromGraph(new FileSourceStub(data))
        val ioCtl = TestProbe()

        val cf = Future {
          StreamIterator(source, new TestActorSystemProxy(systemProbe.ref, system), tm = true)
        }
        val props = systemProbe.expectMsgType[TestActorSystemProxy.Actions.ActorOf]
        props.props.actorClass() + "$" shouldEqual IoCtl.getClass.toString
        systemProbe.reply(ioCtl.ref)

        ioCtl.expectMsgPF() {
          case IoCtl.Commands.Pull => ioCtl.reply(ioCtl.reply(IoCtl.Responses.Chunk(Some(ByteString("a")))))
          case _ =>
        }
        ioCtl.expectMsgPF() {
          case IoCtl.Commands.Pull => ioCtl.reply(ioCtl.reply(IoCtl.Responses.Chunk(Some(ByteString("a")))))
          case _ =>
        }


        val target = Await.result(cf, 1 second)

        val ht1 = Future { target.hasNext() }
        Await.result(ht1, 1 second) shouldEqual true

        val n1 = Future { target.next() }
        ioCtl.expectMsg(IoCtl.Commands.Pull)
        ioCtl.reply(IoCtl.Responses.StreamFailed(new Throwable("x")))
        Await.result(n1, 1 second) shouldEqual "a"

        val ht2 = Future { target.hasNext() }
        Await.result(ht2, 1 second) shouldEqual false
    }
  }

  class FileSourceStub(testElems: List[Byte], failTest: Int = -1)
    extends GraphStageWithMaterializedValue[SourceShape[ByteString], Future[IOResult]] {

    val out = Outlet[ByteString]("FileSource.out")

    override val shape = SourceShape(out)

    override def createLogicAndMaterializedValue(inheritedAttributes: Attributes) = {
      val ioResultPromise = Promise[IOResult]()

      val logic = new GraphStageLogic(shape) with OutHandler {
        handler =>
        setHandler(out, this)

        var elems = testElems


        override def preStart(): Unit = {
          if (failTest == 0)
            ioResultPromise.trySuccess(IOResult(0, Failure(new Throwable("x"))))
        }


        override def onPull(): Unit = {
          /*if (elems.nonEmpty) {
            if (failTest) {
              throw new Throwable("err")
            } else {
              emit(out, ByteString(elems.head))
              elems = elems.tailOrEmpty
            }

          } else {
            success()
          }*/
        }



        private def success(): Unit = {
          completeStage()
          ioResultPromise.trySuccess(IOResult(0, Success(Done)))
        }

        override def onDownstreamFinish(): Unit = success()

        override def postStop(): Unit = {
          ioResultPromise.trySuccess(IOResult(0, Success(Done)))
        }
      }

      (logic, ioResultPromise.future)
    }
  }
}
