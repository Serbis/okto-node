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
import ru.serbis.okto.node.syscoms.pm.{Pm, PmInstall}

class PmInstallSpec extends TestKit(ActorSystem("TestSystem")) with ImplicitSender
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
      val target = system.actorOf(PmInstall.props(Vector("-n", "a"), env, stdIn.ref, stdOut.ref))

      probe.send(target, PmInstall.Commands.Exec)
      stdOut.expectMsg(Stream.Commands.WriteWrapped(ByteString().prompt))
      stdIn.send(target, Stream.Responses.Data(ByteString("code").eoi))
      usercomsRep.expectMsg(UsercomsRep.Commands.Create(UsercomsRep.Responses.UserCommandDefinition("a", "a.js")))
      usercomsRep.reply(UsercomsRep.Responses.Created)
      scriptsRep.expectMsg(ScriptsRep.Commands.Create("a", "code"))
      scriptsRep.reply(ScriptsRep.Responses.Created)
      probe.expectMsg(Pm.Internals.Complete(0, "Success"))
    }

    "Return error if -n option is not presented" in {
      val probe = TestProbe()
      val usercomsRep = TestProbe()
      val scriptsRep = TestProbe()
      val stdOut = TestProbe()
      val stdIn = TestProbe()
      val env = Env(usercomsRep = usercomsRep.ref, scriptsRep = scriptsRep.ref)
      val target = system.actorOf(PmInstall.props(Vector.empty, env, stdIn.ref, stdOut.ref))

      probe.send(target, PmInstall.Commands.Exec)
      probe.expectMsg(Pm.Internals.Complete(10, "Option '-n' is not presented"))
    }

    "Return error if name contain restricted symbols" in {
      val probe = TestProbe()
      val usercomsRep = TestProbe()
      val scriptsRep = TestProbe()
      val stdOut = TestProbe()
      val stdIn = TestProbe()
      val env = Env(usercomsRep = usercomsRep.ref, scriptsRep = scriptsRep.ref)

      val target1 = system.actorOf(PmInstall.props(Vector("-n", "a b"), env, stdIn.ref, stdOut.ref))
      probe.send(target1, PmInstall.Commands.Exec)
      probe.expectMsg(Pm.Internals.Complete(11, "Script name contain some restricted symbols"))

      val target2 = system.actorOf(PmInstall.props(Vector("-n", "..a"), env, stdIn.ref, stdOut.ref))
      probe.send(target2, PmInstall.Commands.Exec)
      probe.expectMsg(Pm.Internals.Complete(11, "Script name contain some restricted symbols"))

      val target3 = system.actorOf(PmInstall.props(Vector("-n", "a/"), env, stdIn.ref, stdOut.ref))
      probe.send(target3, PmInstall.Commands.Exec)
      probe.expectMsg(Pm.Internals.Complete(11, "Script name contain some restricted symbols"))
    }

    "Return error if passed code is empty string" in {
      val probe = TestProbe()
      val usercomsRep = TestProbe()
      val scriptsRep = TestProbe()
      val stdOut = TestProbe()
      val stdIn = TestProbe()
      val env = Env(usercomsRep = usercomsRep.ref, scriptsRep = scriptsRep.ref)
      val target = system.actorOf(PmInstall.props(Vector("-n", "a"), env, stdIn.ref, stdOut.ref))

      probe.send(target, PmInstall.Commands.Exec)
      stdOut.expectMsg(Stream.Commands.WriteWrapped(ByteString().prompt))
      stdIn.send(target, Stream.Responses.Data(ByteString().eoi))
      probe.expectMsg(Pm.Internals.Complete(12, "Presented code is empty string"))
    }

    "Return error if code doest not passed in expected timeout" in {
      val probe = TestProbe()
      val usercomsRep = TestProbe()
      val scriptsRep = TestProbe()
      val stdOut = TestProbe()
      val stdIn = TestProbe()
      val env = Env(usercomsRep = usercomsRep.ref, scriptsRep = scriptsRep.ref)
      val target = system.actorOf(PmInstall.props(Vector("-n", "a"), env, stdIn.ref, stdOut.ref, testMode = true))

      probe.send(target, PmInstall.Commands.Exec)
      stdOut.expectMsg(Stream.Commands.WriteWrapped(ByteString().prompt))
      probe.expectMsg(Pm.Internals.Complete(13, "Code does not presented within 60 seconds"))
    }

    "Return error if command already exist in the system" in {
      val probe = TestProbe()
      val usercomsRep = TestProbe()
      val scriptsRep = TestProbe()
      val stdOut = TestProbe()
      val stdIn = TestProbe()
      val env = Env(usercomsRep = usercomsRep.ref, scriptsRep = scriptsRep.ref)
      val target = system.actorOf(PmInstall.props(Vector("-n", "a"), env, stdIn.ref, stdOut.ref))

      probe.send(target, PmInstall.Commands.Exec)
      stdOut.expectMsg(Stream.Commands.WriteWrapped(ByteString().prompt))
      stdIn.send(target, Stream.Responses.Data(ByteString("code").eoi))
      usercomsRep.expectMsg(UsercomsRep.Commands.Create(UsercomsRep.Responses.UserCommandDefinition("a", "a.js")))
      usercomsRep.reply(UsercomsRep.Responses.Exist)
      probe.expectMsg(Pm.Internals.Complete(14, "Command already installed"))
    }

    "Return error if configuration write error was occurred" in {
      val probe = TestProbe()
      val usercomsRep = TestProbe()
      val scriptsRep = TestProbe()
      val stdOut = TestProbe()
      val stdIn = TestProbe()
      val env = Env(usercomsRep = usercomsRep.ref, scriptsRep = scriptsRep.ref)
      val target = system.actorOf(PmInstall.props(Vector("-n", "a"), env, stdIn.ref, stdOut.ref))

      probe.send(target, PmInstall.Commands.Exec)
      stdOut.expectMsg(Stream.Commands.WriteWrapped(ByteString().prompt))
      stdIn.send(target, Stream.Responses.Data(ByteString("code").eoi))
      usercomsRep.expectMsg(UsercomsRep.Commands.Create(UsercomsRep.Responses.UserCommandDefinition("a", "a.js")))
      usercomsRep.reply(UsercomsRep.Responses.WriteError)
      probe.expectMsg(Pm.Internals.Complete(15, "Configuration IO error"))
    }

    "Return error if usercoms repository does not respond with expected timeout" in {
      val probe = TestProbe()
      val usercomsRep = TestProbe()
      val scriptsRep = TestProbe()
      val stdOut = TestProbe()
      val stdIn = TestProbe()
      val env = Env(usercomsRep = usercomsRep.ref, scriptsRep = scriptsRep.ref)
      val target = system.actorOf(PmInstall.props(Vector("-n", "a"), env, stdIn.ref, stdOut.ref, testMode = true))

      probe.send(target, PmInstall.Commands.Exec)
      stdOut.expectMsg(Stream.Commands.WriteWrapped(ByteString().prompt))
      stdIn.send(target, Stream.Responses.Data(ByteString("code").eoi))
      usercomsRep.expectMsg(UsercomsRep.Commands.Create(UsercomsRep.Responses.UserCommandDefinition("a", "a.js")))
      probe.expectMsg(Pm.Internals.Complete(16, "Internal error type 0"))
    }

    "Return error if script write error was occurred" in {
      val probe = TestProbe()
      val usercomsRep = TestProbe()
      val scriptsRep = TestProbe()
      val stdOut = TestProbe()
      val stdIn = TestProbe()
      val env = Env(usercomsRep = usercomsRep.ref, scriptsRep = scriptsRep.ref)
      val target = system.actorOf(PmInstall.props(Vector("-n", "a"), env, stdIn.ref, stdOut.ref))

      probe.send(target, PmInstall.Commands.Exec)
      stdOut.expectMsg(Stream.Commands.WriteWrapped(ByteString().prompt))
      stdIn.send(target, Stream.Responses.Data(ByteString("code").eoi))
      usercomsRep.expectMsg(UsercomsRep.Commands.Create(UsercomsRep.Responses.UserCommandDefinition("a", "a.js")))
      usercomsRep.reply(UsercomsRep.Responses.Created)
      scriptsRep.expectMsg(ScriptsRep.Commands.Create("a", "code"))
      usercomsRep.reply(ScriptsRep.Responses.WriteError)
      probe.expectMsg(Pm.Internals.Complete(17, "Script IO error"))
    }

    "Return error if scripts repository does not respond with expected timeout" in {
      val probe = TestProbe()
      val usercomsRep = TestProbe()
      val scriptsRep = TestProbe()
      val stdOut = TestProbe()
      val stdIn = TestProbe()
      val env = Env(usercomsRep = usercomsRep.ref, scriptsRep = scriptsRep.ref)
      val target = system.actorOf(PmInstall.props(Vector("-n", "a"), env, stdIn.ref, stdOut.ref, testMode = true))

      probe.send(target, PmInstall.Commands.Exec)
      stdOut.expectMsg(Stream.Commands.WriteWrapped(ByteString().prompt))
      stdIn.send(target, Stream.Responses.Data(ByteString("code").eoi))
      usercomsRep.expectMsg(UsercomsRep.Commands.Create(UsercomsRep.Responses.UserCommandDefinition("a", "a.js")))
      usercomsRep.reply(UsercomsRep.Responses.Created)
      scriptsRep.expectMsg(ScriptsRep.Commands.Create("a", "code"))
      probe.expectMsg(Pm.Internals.Complete(18, "Internal error type 1"))
    }
  }

}
