package ru.serbis.okto.node.unit.syscoms.storage

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import akka.util.ByteString
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import ru.serbis.okto.node.common.Env
import ru.serbis.okto.node.log.{StdOutLogger, StreamLogger}
import ru.serbis.okto.node.reps.StorageRep
import ru.serbis.okto.node.reps.StorageRep.Responses.FileInfo
import ru.serbis.okto.node.syscoms.storage.{Storage, StorageInfo, StorageRead}

class StorageReadSpec extends TestKit(ActorSystem("TestSystem")) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll with StreamLogger {


  override protected def beforeAll(): Unit = {
    initializeGlobalLogger(system)
    logger.addDestination(system.actorOf(StdOutLogger.props))
  }


  "StorageRead" must {
    "Process positive test" in {
      val probe = TestProbe()
      val storageRep = TestProbe()
      val stdOut = TestProbe()
      val stdIn = TestProbe()
      val env = Env(storageRep = storageRep.ref)
      val target = system.actorOf(StorageRead.props(Vector("file", "10", "20"), env, stdIn.ref, stdOut.ref))

      probe.send(target, StorageRead.Commands.Exec)
      storageRep.expectMsg(StorageRep.Commands.ReadFragment("file", 10, 20))
      storageRep.reply(StorageRep.Responses.BlobData(ByteString("abc")))
      probe.expectMsg(Storage.Internals.Complete(0, "abc"))
    }

    "Return error if insufficient args count problem as occurred" in {
      val probe = TestProbe()
      val storageRep = TestProbe()
      val stdOut = TestProbe()
      val stdIn = TestProbe()
      val env = Env(storageRep = storageRep.ref)
      val target = system.actorOf(StorageRead.props(Vector("file", "10"), env, stdIn.ref, stdOut.ref))

      probe.send(target, StorageRead.Commands.Exec)
      probe.expectMsg(Storage.Internals.Complete(20, "Required 3 args, but found 2"))
    }

    "Return error if some of numerical args is not number" in {
      val probe = TestProbe()
      val storageRep = TestProbe()
      val stdOut = TestProbe()
      val stdIn = TestProbe()
      val env = Env(storageRep = storageRep.ref)
      val target = system.actorOf(StorageRead.props(Vector("file", "X", "20"), env, stdIn.ref, stdOut.ref))

      probe.send(target, StorageRead.Commands.Exec)
      probe.expectMsg(Storage.Internals.Complete(21, "Args 2 and 3 must be a number"))
    }


    "Return error if storage repository does not respond with expected timeout" in {
      val probe = TestProbe()
      val storageRep = TestProbe()
      val stdOut = TestProbe()
      val stdIn = TestProbe()
      val env = Env(storageRep = storageRep.ref)
      val target = system.actorOf(StorageRead.props(Vector("file", "10", "20"), env, stdIn.ref, stdOut.ref, testMode = true))

      probe.send(target, StorageRead.Commands.Exec)
      probe.expectMsg(Storage.Internals.Complete(22, "Internal error type 0"))
    }

    "Return error if storage repository respond with operation failed message" in {
      val probe = TestProbe()
      val storageRep = TestProbe()
      val stdOut = TestProbe()
      val stdIn = TestProbe()
      val env = Env(storageRep = storageRep.ref)
      val target = system.actorOf(StorageRead.props(Vector("file", "10", "20"), env, stdIn.ref, stdOut.ref))

      probe.send(target, StorageRead.Commands.Exec)
      storageRep.expectMsg(StorageRep.Commands.ReadFragment("file", 10, 20))
      storageRep.reply(StorageRep.Responses.OperationError("ex"))
      probe.expectMsg(Storage.Internals.Complete(23, s"Unable to read file: ex"))
    }
  }
}