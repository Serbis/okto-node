package ru.serbis.okto.node.unit.runtime.senv.vstorage

import java.io.File
import java.nio.file.Path

import akka.Done
import akka.actor.ActorSystem
import akka.stream.scaladsl.Source
import akka.stream.stage.{GraphStageLogic, GraphStageWithMaterializedValue, OutHandler}
import akka.stream.{Attributes, IOResult, Outlet, SourceShape}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import akka.util.ByteString
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import ru.serbis.okto.node.common.ReachTypes.ReachList
import ru.serbis.okto.node.log.{StdOutLogger, StreamLogger}
import ru.serbis.okto.node.proxy.system.TestActorSystemProxy
import ru.serbis.okto.node.reps.StorageRep
import ru.serbis.okto.node.runtime.senv.vstorage.{IoCtl, StreamIterator, StreamSeparatedIterator, VStorage}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future, Promise}
import scala.util.Success

class VStorageSpec extends TestKit(ActorSystem("TestSystem")) with ImplicitSender with WordSpecLike with Matchers with BeforeAndAfterAll with StreamLogger {

  override protected def beforeAll(): Unit = {
    initializeGlobalLogger(system)
    logger.addDestination(system.actorOf(StdOutLogger.props))
  }

  "VStorage" must {
    "For write method" should {
      "Send request to the storage and return true if it was success" in {
        val storage = TestProbe()
        val target = new VStorage(storage.ref, null)

        val shellFut = Future {
          target.write("a", "b")
        }
        storage.expectMsg(StorageRep.Commands.ReWrite("a", ByteString("b")))
        storage.reply(StorageRep.Responses.OperationSuccess)
        val result = Await.result(shellFut, 1 second)
        result shouldEqual true
      }

      "Send request to the storage and return false if it was failed" in {
        val storage = TestProbe()
        val target = new VStorage(storage.ref, null)

        val shellFut = Future {
          target.write("a", "b")
        }
        storage.expectMsg(StorageRep.Commands.ReWrite("a", ByteString("b")))
        storage.reply(StorageRep.Responses.OperationError("e"))
        val result = Await.result(shellFut, 1 second)
        result shouldEqual false
      }

      "Send request to the storage and return false if storage does not respond" in {
        val storage = TestProbe()
        val target = new VStorage(storage.ref, null, tm = true)

        val shellFut = Future {
          target.write("a", "b")
        }
        storage.expectMsg(StorageRep.Commands.ReWrite("a", ByteString("b")))
        val result = Await.result(shellFut, 1 second)
        result shouldEqual false
      }
    }

    "For append method" should {
      "Send request to the storage and return true if it was success" in {
        val storage = TestProbe()
        val target = new VStorage(storage.ref, null)

        val shellFut = Future {
          target.append("a", "b")
        }
        storage.expectMsg(StorageRep.Commands.Append("a", ByteString("b")))
        storage.reply(StorageRep.Responses.OperationSuccess)
        val result = Await.result(shellFut, 1 second)
        result shouldEqual true
      }

      "Send request to the storage and return false if it was failed" in {
        val storage = TestProbe()
        val target = new VStorage(storage.ref, null)

        val shellFut = Future {
          target.append("a", "b")
        }
        storage.expectMsg(StorageRep.Commands.Append("a", ByteString("b")))
        storage.reply(StorageRep.Responses.OperationError("e"))
        val result = Await.result(shellFut, 1 second)
        result shouldEqual false
      }

      "Send request to the storage and return false if srorage does not respond" in {
        val storage = TestProbe()
        val target = new VStorage(storage.ref, null, tm = true)

        val shellFut = Future {
          target.append("a", "b")
        }
        storage.expectMsg(StorageRep.Commands.Append("a", ByteString("b")))
        val result = Await.result(shellFut, 1 second)
        result shouldEqual false
      }
    }

    "For delete method" should {
      "Send request to the storage and return true if it was success" in {
        val storage = TestProbe()
        val target = new VStorage(storage.ref, null)

        val shellFut = Future {
          target.delete("a")
        }
        storage.expectMsg(StorageRep.Commands.Delete("a"))
        storage.reply(StorageRep.Responses.OperationSuccess)
        val result = Await.result(shellFut, 1 second)
        result shouldEqual true
      }

      "Send request to the storage and return false if it was failed" in {
        val storage = TestProbe()
        val target = new VStorage(storage.ref, null)

        val shellFut = Future {
          target.delete("a")
        }
        storage.expectMsg(StorageRep.Commands.Delete("a"))
        storage.reply(StorageRep.Responses.OperationError("e"))
        val result = Await.result(shellFut, 1 second)
        result shouldEqual false
      }

      "Send request to the storage and return false if srorage does not respond" in {
        val storage = TestProbe()
        val target = new VStorage(storage.ref, null, tm = true)

        val shellFut = Future {
          target.delete("a")
        }
        storage.expectMsg(StorageRep.Commands.Delete("a"))
        val result = Await.result(shellFut, 1 second)
        result shouldEqual false
      }
    }

    "For read method" should {
      "Send request to the storage and return data if it was success" in {
        val storage = TestProbe()
        val target = new VStorage(storage.ref, null)

        val shellFut = Future {
          target.read("a")
        }
        storage.expectMsg(StorageRep.Commands.Read("a"))
        storage.reply(StorageRep.Responses.BlobData(ByteString("b")))
        val result = Await.result(shellFut, 1 second)
        result shouldEqual "b"
      }

      "Send request to the storage and return null if it was failed" in {
        val storage = TestProbe()
        val target = new VStorage(storage.ref, null)

        val shellFut = Future {
          target.read("a")
        }
        storage.expectMsg(StorageRep.Commands.Read("a"))
        storage.reply(StorageRep.Responses.OperationError("e"))
        val result = Await.result(shellFut, 1 second)
        result shouldEqual null
      }

      "Send request to the storage and return null if srorage does not respond" in {
        val storage = TestProbe()
        val target = new VStorage(storage.ref, null, tm = true)

        val shellFut = Future {
          target.read("a")
        }
        storage.expectMsg(StorageRep.Commands.Read("a"))
        val result = Await.result(shellFut, 1 second)
        result shouldEqual null
      }
    }

    "For readStream method" should {
      //FIXME unable to pass this text because StreamIterator class invoke external actors in constructor
      /*"Send request to the storage and return iterator if it was success" in {
        val storage = TestProbe()
        val systemProbe = TestProbe()
        val target = new VStorage(storage.ref, new TestActorSystemProxy(systemProbe.ref, system))

        val shellFut = Future {
          target.readStream("a", 2)
        }
        storage.expectMsg(StorageRep.Commands.ReadAsStream("a", 2))
        val data = List(97.toByte, 98.toByte, 99.toByte, 100.toByte, 101.toByte)
        val source = Source.fromGraph(new FileSourceStub(new File("123").toPath, 1, 0, data))
        storage.reply(StorageRep.Responses.StreamData(source))
        val result = Await.result(shellFut, 1 second)
        result.isInstanceOf[StreamIterator] shouldEqual true
      }*/

      "Send request to the storage and return null if it was failed" in {
        val storage = TestProbe()
        val target = new VStorage(storage.ref, null)

        val shellFut = Future {
          target.readStream("a", 2)
        }
        storage.expectMsg(StorageRep.Commands.ReadAsStream("a", 2))
        storage.reply(StorageRep.Responses.OperationError("e"))
        val result = Await.result(shellFut, 1 second)
        result shouldEqual null
      }

      "Send request to the storage and return null if srorage does not respond" in {
        val storage = TestProbe()
        val target = new VStorage(storage.ref, null, tm = true)

        val shellFut = Future {
          target.readStream("a", 2)
        }
        storage.expectMsg(StorageRep.Commands.ReadAsStream("a", 2))
        val result = Await.result(shellFut, 1 second)
        result shouldEqual null
      }
    }

    "For readStreamSep method" should {
      //FIXME unable to pass this text because StreamSeparatedIterator class invoke external actors in constructor
      /*"Send request to the storage and return iterator if it was success" in {
        val storage = TestProbe()
        val target = new VStorage(storage.ref, null)

        val shellFut = Future {
          target.readStreamSep("a", "s")
        }
        storage.expectMsg(StorageRep.Commands.ReadAsStream("a", 1024))
        val source = Source.fromGraph(new FileSourceStub(new File("123").toPath, 1024, 0, List.empty))
        storage.reply(StorageRep.Responses.StreamData(source))
        val result = Await.result(shellFut, 1 second)
        result.isInstanceOf[StreamSeparatedIterator] shouldEqual true
      }*/

      "Send request to the storage and return null if it was failed" in {
        val storage = TestProbe()
        val target = new VStorage(storage.ref, null)

        val shellFut = Future {
          target.readStreamSep("a", "s")
        }
        storage.expectMsg(StorageRep.Commands.ReadAsStream("a",1000000))
        storage.reply(StorageRep.Responses.OperationError("e"))
        val result = Await.result(shellFut, 1 second)
        result shouldEqual null
      }

      "Send request to the storage and return null if srorage does not respond" in {
        val storage = TestProbe()
        val target = new VStorage(storage.ref, null, tm = true)

        val shellFut = Future {
          target.readStreamSep("a", "s")
        }
        storage.expectMsg(StorageRep.Commands.ReadAsStream("a", 1000000))
        val result = Await.result(shellFut, 1 second)
        result shouldEqual null
      }
    }

    class FileSourceStub(path: Path, chunkSize: Int, startPosition: Long, testElems: List[Byte])
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
            if (testElems.nonEmpty) {
              emit(out, ByteString(elems.head))
              elems = elems.tailOrEmpty
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
          }
        }

        (logic, ioResultPromise.future)
      }
    }
  }
}