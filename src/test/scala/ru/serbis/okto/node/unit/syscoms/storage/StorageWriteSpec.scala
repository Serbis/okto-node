package ru.serbis.okto.node.unit.syscoms.storage

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import akka.util.ByteString
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import ru.serbis.okto.node.common.Env
import ru.serbis.okto.node.log.{StdOutLogger, StreamLogger}
import ru.serbis.okto.node.reps.StorageRep
import ru.serbis.okto.node.runtime.Stream
import ru.serbis.okto.node.syscoms.storage.{Storage, StorageWrite}
import ru.serbis.okto.node.common.ReachTypes.ReachByteString

class StorageWriteSpec extends TestKit(ActorSystem("TestSystem")) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll with StreamLogger {


  override protected def beforeAll(): Unit = {
    initializeGlobalLogger(system)
    logger.addDestination(system.actorOf(StdOutLogger.props))
  }


  "StorageWrite" must {
    "Process positive test" in {
      val probe = TestProbe()
      val storageRep = TestProbe()
      val stdOut = TestProbe()
      val stdIn = TestProbe()
      val env = Env(storageRep = storageRep.ref)
      val target = system.actorOf(StorageWrite.props(Vector("file", "10", "20"), env, stdIn.ref, stdOut.ref))

      probe.send(target, StorageWrite.Commands.Exec)
      stdOut.expectMsg(Stream.Commands.WriteWrapped(ByteString().prompt))
      stdIn.send(target, Stream.Responses.Data(ByteString("abc").eoi))
      storageRep.expectMsg(StorageRep.Commands.WriteFragment("file", 10, 20, ByteString("abc")))
      storageRep.reply(StorageRep.Responses.OperationSuccess)
      probe.expectMsg(Storage.Internals.Complete(0, "OK"))
    }

    "Return error if insufficient args count problem as occurred" in {
      val probe = TestProbe()
      val storageRep = TestProbe()
      val stdOut = TestProbe()
      val stdIn = TestProbe()
      val env = Env(storageRep = storageRep.ref)
      val target = system.actorOf(StorageWrite.props(Vector("file", "10"), env, stdIn.ref, stdOut.ref))

      probe.send(target, StorageWrite.Commands.Exec)
      probe.expectMsg(Storage.Internals.Complete(40, "Required 3 args, but found 2"))
    }

    "Return error if some of numerical args is not number" in {
      val probe = TestProbe()
      val storageRep = TestProbe()
      val stdOut = TestProbe()
      val stdIn = TestProbe()
      val env = Env(storageRep = storageRep.ref)
      val target = system.actorOf(StorageWrite.props(Vector("file", "X", "20"), env, stdIn.ref, stdOut.ref))

      probe.send(target, StorageWrite.Commands.Exec)
      probe.expectMsg(Storage.Internals.Complete(41, "Args 2 and 3 must be a number"))
    }

    //NOT ACTUAL
    /*"Return error if passed fragment is empty string" in {
      val probe = TestProbe()
      val storageRep = TestProbe()
      val stdOut = TestProbe()
      val stdIn = TestProbe()
      val env = Env(storageRep = storageRep.ref)
      val target = system.actorOf(StorageWrite.props(Vector("file", "10", "20"), env, stdIn.ref, stdOut.ref))

      probe.send(target, StorageWrite.Commands.Exec)
      stdOut.expectMsg(Stream.Commands.WriteWrapped(ByteString().prompt))
      stdIn.send(target, Stream.Responses.Data(ByteString().eoi))
      probe.expectMsg(Storage.Internals.Complete(42, "Presented fragment is empty string"))
    }*/

    "Return error if code doest not passed in expected timeout" in {
      val probe = TestProbe()
      val storageRep = TestProbe()
      val stdOut = TestProbe()
      val stdIn = TestProbe()
      val env = Env(storageRep = storageRep.ref)
      val target = system.actorOf(StorageWrite.props(Vector("file", "10", "20"), env, stdIn.ref, stdOut.ref, testMode = true))

      probe.send(target, StorageWrite.Commands.Exec)
      stdOut.expectMsg(Stream.Commands.WriteWrapped(ByteString().prompt))
      probe.expectMsg(Storage.Internals.Complete(43, "Fragment does not presented within 60 seconds"))
    }


    "Return error if storage repository does not respond with expected timeout" in {
      val probe = TestProbe()
      val storageRep = TestProbe()
      val stdOut = TestProbe()
      val stdIn = TestProbe()
      val env = Env(storageRep = storageRep.ref)
      val target = system.actorOf(StorageWrite.props(Vector("file", "10", "20"), env, stdIn.ref, stdOut.ref, testMode = true))

      probe.send(target, StorageWrite.Commands.Exec)
      stdOut.expectMsg(Stream.Commands.WriteWrapped(ByteString().prompt))
      stdIn.send(target, Stream.Responses.Data(ByteString("abc").eoi))
      probe.expectMsg(Storage.Internals.Complete(44, "Internal error type 0"))
    }

    "Return error if storage repository respond with operation failed message" in {
      val probe = TestProbe()
      val storageRep = TestProbe()
      val stdOut = TestProbe()
      val stdIn = TestProbe()
      val env = Env(storageRep = storageRep.ref)
      val target = system.actorOf(StorageWrite.props(Vector("file", "10", "20"), env, stdIn.ref, stdOut.ref))

      probe.send(target, StorageWrite.Commands.Exec)
      stdOut.expectMsg(Stream.Commands.WriteWrapped(ByteString().prompt))
      stdIn.send(target, Stream.Responses.Data(ByteString("abc").eoi))
      storageRep.expectMsg(StorageRep.Commands.WriteFragment("file", 10, 20, ByteString("abc")))
      storageRep.reply(StorageRep.Responses.OperationError("ex"))
      probe.expectMsg(Storage.Internals.Complete(45, s"Unable to write file: ex"))
    }
  }
}