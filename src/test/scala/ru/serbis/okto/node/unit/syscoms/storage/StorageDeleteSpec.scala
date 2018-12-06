package ru.serbis.okto.node.unit.syscoms.storage

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import akka.util.ByteString
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import ru.serbis.okto.node.common.Env
import ru.serbis.okto.node.log.{StdOutLogger, StreamLogger}
import ru.serbis.okto.node.reps.StorageRep
import ru.serbis.okto.node.syscoms.storage.{Storage, StorageDelete}

class StorageDeleteSpec extends TestKit(ActorSystem("TestSystem")) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll with StreamLogger {


  override protected def beforeAll(): Unit = {
    initializeGlobalLogger(system)
    logger.addDestination(system.actorOf(StdOutLogger.props))
  }


  "StorageDelete" must {
    "Process positive test" in {
      val probe = TestProbe()
      val storageRep = TestProbe()
      val stdOut = TestProbe()
      val stdIn = TestProbe()
      val env = Env(storageRep = storageRep.ref)
      val target = system.actorOf(StorageDelete.props(Vector("file"), env, stdIn.ref, stdOut.ref))

      probe.send(target, StorageDelete.Commands.Exec)
      storageRep.expectMsg(StorageRep.Commands.Delete("file"))
      storageRep.reply(StorageRep.Responses.OperationSuccess)
      probe.expectMsg(Storage.Internals.Complete(0, "OK"))
    }

    "Return error if insufficient args count problem as occurred" in {
      val probe = TestProbe()
      val storageRep = TestProbe()
      val stdOut = TestProbe()
      val stdIn = TestProbe()
      val env = Env(storageRep = storageRep.ref)
      val target = system.actorOf(StorageDelete.props(Vector.empty, env, stdIn.ref, stdOut.ref))

      probe.send(target, StorageDelete.Commands.Exec)
      probe.expectMsg(Storage.Internals.Complete(30, "Required file name"))
    }


    "Return error if storage repository does not respond with expected timeout" in {
      val probe = TestProbe()
      val storageRep = TestProbe()
      val stdOut = TestProbe()
      val stdIn = TestProbe()
      val env = Env(storageRep = storageRep.ref)
      val target = system.actorOf(StorageDelete.props(Vector("file"), env, stdIn.ref, stdOut.ref, testMode = true))

      probe.send(target, StorageDelete.Commands.Exec)
      probe.expectMsg(Storage.Internals.Complete(31, "Internal error type 0"))
    }

    "Return error if storage repository respond with operation failed message" in {
      val probe = TestProbe()
      val storageRep = TestProbe()
      val stdOut = TestProbe()
      val stdIn = TestProbe()
      val env = Env(storageRep = storageRep.ref)
      val target = system.actorOf(StorageDelete.props(Vector("file"), env, stdIn.ref, stdOut.ref))

      probe.send(target, StorageDelete.Commands.Exec)
      storageRep.expectMsg(StorageRep.Commands.Delete("file"))
      storageRep.reply(StorageRep.Responses.OperationError("ex"))
      probe.expectMsg(Storage.Internals.Complete(32, s"Unable to delete file: ex"))
    }
  }
}