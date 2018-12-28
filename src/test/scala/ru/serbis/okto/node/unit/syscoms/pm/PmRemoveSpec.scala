package ru.serbis.okto.node.unit.syscoms.pm

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import akka.util.ByteString
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import ru.serbis.okto.node.common.Env
import ru.serbis.okto.node.common.ReachTypes.ReachByteString
import ru.serbis.okto.node.log.{StdOutLogger, StreamLogger}
import ru.serbis.okto.node.reps.{ScriptsRep, UsercomsRep}
import ru.serbis.okto.node.runtime.Stream
import ru.serbis.okto.node.syscoms.pm.{Pm, PmInstall, PmRemove}

class PmRemoveSpec extends TestKit(ActorSystem("TestSystem")) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll with StreamLogger {


  override protected def beforeAll(): Unit = {
    initializeGlobalLogger(system)
    logger.addDestination(system.actorOf(StdOutLogger.props))
  }


  "PmInstall" must {
    "Process positive test" in {
      val probe = TestProbe()
      val usercomsRep = TestProbe()
      val scriptsRep = TestProbe()
      val stdOut = TestProbe()
      val stdIn = TestProbe()
      val env = Env(usercomsRep = usercomsRep.ref, scriptsRep = scriptsRep.ref)
      val target = system.actorOf(PmRemove.props(Vector("a"), env, stdIn.ref, stdOut.ref))

      probe.send(target, PmRemove.Commands.Exec)
      usercomsRep.expectMsg(UsercomsRep.Commands.Remove("a"))
      usercomsRep.reply(UsercomsRep.Responses.Removed)
      scriptsRep.expectMsg(ScriptsRep.Commands.Remove("a"))
      scriptsRep.reply(ScriptsRep.Responses.Removed)
      probe.expectMsg(Pm.Internals.Complete(0, "Success"))
    }

    "Return error if script name option is not presented" in {
      val probe = TestProbe()
      val usercomsRep = TestProbe()
      val scriptsRep = TestProbe()
      val stdOut = TestProbe()
      val stdIn = TestProbe()
      val env = Env(usercomsRep = usercomsRep.ref, scriptsRep = scriptsRep.ref)
      val target = system.actorOf(PmRemove.props(Vector.empty, env, stdIn.ref, stdOut.ref))

      probe.send(target, PmRemove.Commands.Exec)
      probe.expectMsg(Pm.Internals.Complete(20, "Command name is not presented"))
    }

    "Return error if name contain restricted symbols" in {
      val probe = TestProbe()
      val usercomsRep = TestProbe()
      val scriptsRep = TestProbe()
      val stdOut = TestProbe()
      val stdIn = TestProbe()
      val env = Env(usercomsRep = usercomsRep.ref, scriptsRep = scriptsRep.ref)

      val target1 = system.actorOf(PmRemove.props(Vector("a b"), env, stdIn.ref, stdOut.ref))
      probe.send(target1, PmRemove.Commands.Exec)
      probe.expectMsg(Pm.Internals.Complete(21, "Script name contain restricted symbols"))

      val target2 = system.actorOf(PmRemove.props(Vector("..a"), env, stdIn.ref, stdOut.ref))
      probe.send(target2, PmRemove.Commands.Exec)
      probe.expectMsg(Pm.Internals.Complete(21, "Script name contain restricted symbols"))

      val target3 = system.actorOf(PmRemove.props(Vector("a/"), env, stdIn.ref, stdOut.ref))
      probe.send(target3, PmRemove.Commands.Exec)
      probe.expectMsg(Pm.Internals.Complete(21, "Script name contain restricted symbols"))
    }

    "Return error if command does not exist in the system" in {
      val probe = TestProbe()
      val usercomsRep = TestProbe()
      val scriptsRep = TestProbe()
      val stdOut = TestProbe()
      val stdIn = TestProbe()
      val env = Env(usercomsRep = usercomsRep.ref, scriptsRep = scriptsRep.ref)
      val target = system.actorOf(PmRemove.props(Vector("a"), env, stdIn.ref, stdOut.ref))

      probe.send(target, PmRemove.Commands.Exec)
      usercomsRep.expectMsg(UsercomsRep.Commands.Remove("a"))
      usercomsRep.reply(UsercomsRep.Responses.NotExist)
      probe.expectMsg(Pm.Internals.Complete(22, "Command does not exist"))
    }

    "Return error if configuration write error was occurred" in {
      val probe = TestProbe()
      val usercomsRep = TestProbe()
      val scriptsRep = TestProbe()
      val stdOut = TestProbe()
      val stdIn = TestProbe()
      val env = Env(usercomsRep = usercomsRep.ref, scriptsRep = scriptsRep.ref)
      val target = system.actorOf(PmRemove.props(Vector("a"), env, stdIn.ref, stdOut.ref))

      probe.send(target, PmRemove.Commands.Exec)
      usercomsRep.expectMsg(UsercomsRep.Commands.Remove("a"))
      usercomsRep.reply(UsercomsRep.Responses.WriteError)
      probe.expectMsg(Pm.Internals.Complete(23, "Configuration IO error"))
    }

    "Return error if usercoms repository does not respond with expected timeout" in {
      val probe = TestProbe()
      val usercomsRep = TestProbe()
      val scriptsRep = TestProbe()
      val stdOut = TestProbe()
      val stdIn = TestProbe()
      val env = Env(usercomsRep = usercomsRep.ref, scriptsRep = scriptsRep.ref)
      val target = system.actorOf(PmRemove.props(Vector("a"), env, stdIn.ref, stdOut.ref, testMode = true))

      probe.send(target, PmRemove.Commands.Exec)
      usercomsRep.expectMsg(UsercomsRep.Commands.Remove("a"))
      probe.expectMsg(Pm.Internals.Complete(24, "Internal error type 0"))
    }

    "Return error if script remove error was occurred" in {
      val probe = TestProbe()
      val usercomsRep = TestProbe()
      val scriptsRep = TestProbe()
      val stdOut = TestProbe()
      val stdIn = TestProbe()
      val env = Env(usercomsRep = usercomsRep.ref, scriptsRep = scriptsRep.ref)
      val target = system.actorOf(PmRemove.props(Vector("a"), env, stdIn.ref, stdOut.ref))

      probe.send(target, PmRemove.Commands.Exec)
      usercomsRep.expectMsg(UsercomsRep.Commands.Remove("a"))
      usercomsRep.reply(UsercomsRep.Responses.Removed)
      scriptsRep.expectMsg(ScriptsRep.Commands.Remove("a"))
      scriptsRep.reply(ScriptsRep.Responses.WriteError)
      probe.expectMsg(Pm.Internals.Complete(25, "Script IO error"))
    }

    "Return error if scripts repository does not respond with expected timeout" in {
      val probe = TestProbe()
      val usercomsRep = TestProbe()
      val scriptsRep = TestProbe()
      val stdOut = TestProbe()
      val stdIn = TestProbe()
      val env = Env(usercomsRep = usercomsRep.ref, scriptsRep = scriptsRep.ref)
      val target = system.actorOf(PmRemove.props(Vector("a"), env, stdIn.ref, stdOut.ref, testMode = true))

      probe.send(target, PmRemove.Commands.Exec)
      usercomsRep.expectMsg(UsercomsRep.Commands.Remove("a"))
      usercomsRep.reply(UsercomsRep.Responses.Removed)
      scriptsRep.expectMsg(ScriptsRep.Commands.Remove("a"))
      probe.expectMsg(Pm.Internals.Complete(26, "Internal error type 1"))
    }
  }
}