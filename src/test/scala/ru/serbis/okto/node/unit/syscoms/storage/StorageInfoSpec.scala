package ru.serbis.okto.node.unit.syscoms.storage

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import ru.serbis.okto.node.common.Env
import ru.serbis.okto.node.log.{StdOutLogger, StreamLogger}
import ru.serbis.okto.node.reps.StorageRep
import ru.serbis.okto.node.reps.StorageRep.Responses.FileInfo
import ru.serbis.okto.node.syscoms.storage.{Storage, StorageInfo}

class StorageInfoSpec extends TestKit(ActorSystem("TestSystem")) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll with StreamLogger {


  override protected def beforeAll(): Unit = {
    initializeGlobalLogger(system)
    logger.addDestination(system.actorOf(StdOutLogger.props))
  }


  "StorageInfo" must {
    "Process positive test" in {
      val probe = TestProbe()
      val storageRep = TestProbe()
      val stdOut = TestProbe()
      val stdIn = TestProbe()
      val env = Env(storageRep = storageRep.ref)
      val target = system.actorOf(StorageInfo.props(Vector("a", "b", "c"), env, stdIn.ref, stdOut.ref))

      probe.send(target, StorageInfo.Commands.Exec)
      storageRep.expectMsg(StorageRep.Commands.GetInfo(List("a", "b", "c")))
      storageRep.reply(StorageRep.Responses.FilesInfo(Map(
        "a" -> Right(FileInfo(100, 1000000, 2000000)),
        "b" -> Left(new Throwable("x")),
        "c" -> Right(FileInfo(102, 1002000, 2002000))
      )))
      probe.expectMsg(Storage.Internals.Complete(0, "{\n\t\"a\" : {\n\t\t\"size\" : 100,\n\t\t\"created\" : 1000000,\n\t\t\"modified\" : 2000000\n\t},\n\t\"b\" : {\n\t\t\"error\" : \"java.lang.Throwable: x\"\n\t},\n\t\"c\" : {\n\t\t\"size\" : 102,\n\t\t\"created\" : 1002000,\n\t\t\"modified\" : 2002000\n\t}\n}"))
    }


    "Return error if storage repository does not respond with expected timeout" in {
      val probe = TestProbe()
      val storageRep = TestProbe()
      val stdOut = TestProbe()
      val stdIn = TestProbe()
      val env = Env(storageRep = storageRep.ref)
      val target = system.actorOf(StorageInfo.props(Vector("a", "b", "c"), env, stdIn.ref, stdOut.ref, testMode = true))

      probe.send(target, StorageInfo.Commands.Exec)
      storageRep.expectMsg(StorageRep.Commands.GetInfo(List("a", "b", "c")))
      probe.expectMsg(Storage.Internals.Complete(11, "Internal error type 0"))
    }

    "Return error if storage repository respond with operation failed message" in {
      val probe = TestProbe()
      val storageRep = TestProbe()
      val stdOut = TestProbe()
      val stdIn = TestProbe()
      val env = Env(storageRep = storageRep.ref)
      val target = system.actorOf(StorageInfo.props(Vector("a", "b", "c"), env, stdIn.ref, stdOut.ref, testMode = true))

      probe.send(target, StorageInfo.Commands.Exec)
      storageRep.expectMsg(StorageRep.Commands.GetInfo(List("a", "b", "c")))
      storageRep.reply(StorageRep.Responses.OperationError("ex"))
      probe.expectMsg(Storage.Internals.Complete(12, s"Unable to read storage: ex"))
    }
  }
}