package ru.serbis.okto.node.unit.runtime.senv.vstorage

import java.io.File
import java.nio.file.Path

import akka.Done
import akka.actor.{ActorSystem, PoisonPill}
import akka.stream.scaladsl.{Sink, Source}
import akka.stream.stage.{GraphStageLogic, GraphStageWithMaterializedValue, OutHandler}
import akka.stream._
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import akka.util.ByteString
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import ru.serbis.okto.node.common.ReachTypes.ReachList
import ru.serbis.okto.node.log.{StdOutLogger, StreamLogger}
import ru.serbis.okto.node.proxy.system.TestActorSystemProxy
import ru.serbis.okto.node.reps.StorageRep
import ru.serbis.okto.node.runtime.Stream
import ru.serbis.okto.node.runtime.senv.vstorage.{IoCtl, StreamIterator, StreamSeparatedIterator, VStorage}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future, Promise}
import scala.util.{Failure, Success}

class IoCtlSpec extends TestKit(ActorSystem("TestSystem")) with ImplicitSender with WordSpecLike with Matchers with BeforeAndAfterAll with StreamLogger {

  implicit val mat = ActorMaterializer.create(system)

  override protected def beforeAll(): Unit = {
    initializeGlobalLogger(system)
    logger.addDestination(system.actorOf(StdOutLogger.props))
  }

  "IoCtl" must {
    "After star must correct initialize iterator" should {
      "Read all stream by chunks" in {
        val probe = TestProbe()
        val target = system.actorOf(IoCtl.props)

        val data = ByteString("abcde").toList
        Source.fromGraph(new FileSourceStub(data)) to Sink.actorRefWithAck(target, IoCtl.Internals.Init, IoCtl.Internals.Ack, IoCtl.Internals.Complete) run()
        probe.send(target, IoCtl.Commands.Pull)
        probe.expectMsg(IoCtl.Responses.Chunk(Some(ByteString("a"))))
        probe.send(target, IoCtl.Commands.Pull)
        probe.expectMsg(IoCtl.Responses.Chunk(Some(ByteString("b"))))
        probe.send(target, IoCtl.Commands.Pull)
        probe.expectMsg(IoCtl.Responses.Chunk(Some(ByteString("c"))))
        probe.send(target, IoCtl.Commands.Pull)
        probe.expectMsg(IoCtl.Responses.Chunk(Some(ByteString("d"))))
        probe.send(target, IoCtl.Commands.Pull)
        probe.expectMsg(IoCtl.Responses.Chunk(Some(ByteString("e"))))
        probe.send(target, IoCtl.Commands.Pull)
        probe.expectMsg(IoCtl.Responses.Chunk(None))
      }

      "Fail pull request if stream was failed" in {
        val probe = TestProbe()
        val target = system.actorOf(IoCtl.props)

        val data = ByteString("abcde").toList
        Source.fromGraph(new FileSourceStub(data, failTest = true)) to Sink.actorRefWithAck(target, IoCtl.Internals.Init, IoCtl.Internals.Ack, IoCtl.Internals.Complete) run()
        probe.send(target, IoCtl.Commands.Pull)
        probe.expectMsgType[IoCtl.Responses.StreamFailed]
      }
    }


    class FileSourceStub(testElems: List[Byte], failTest: Boolean = false)
      extends GraphStageWithMaterializedValue[SourceShape[ByteString], Future[IOResult]] {

      val out = Outlet[ByteString]("FileSource.out")

      override val shape = SourceShape(out)

      override def createLogicAndMaterializedValue(inheritedAttributes: Attributes) = {
        val ioResultPromise = Promise[IOResult]()

        val logic = new GraphStageLogic(shape) with OutHandler {
          handler =>
          setHandler(out, this)

          var elems = testElems

          override def onPull(): Unit = {
            if (elems.nonEmpty) {
              if (failTest) {
                throw new Throwable("err")
              } else {
                emit(out, ByteString(elems.head))
                elems = elems.tailOrEmpty
              }

            } else {
              success()
            }
          }



          private def success(): Unit = {
            completeStage()
            ioResultPromise.trySuccess(IOResult(0, Success(Done)))
          }

          override def onDownstreamFinish(): Unit = success()

          override def postStop(): Unit = {
            ioResultPromise.trySuccess(IOResult(0, Success(Done)))
            println("Stream terminated")
          }
        }

        (logic, ioResultPromise.future)
      }
    }
  }
}