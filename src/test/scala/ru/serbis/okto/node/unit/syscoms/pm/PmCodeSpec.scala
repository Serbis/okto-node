package ru.serbis.okto.node.unit.syscoms.pm

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import ru.serbis.okto.node.common.Env
import ru.serbis.okto.node.log.{StdOutLogger, StreamLogger}
import ru.serbis.okto.node.reps.ScriptsRep
import ru.serbis.okto.node.syscoms.pm.{Pm, PmCode, PmList}

class PmCodeSpec extends TestKit(ActorSystem("TestSystem")) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll with StreamLogger {


  override protected def beforeAll(): Unit = {
    initializeGlobalLogger(system)
    logger.addDestination(system.actorOf(StdOutLogger.props))
  }


  "PmGet" must {
    "Process positive test" in {
      val probe = TestProbe()
      val usercomsRep = TestProbe()
      val scriptsRep = TestProbe()
      val stdOut = TestProbe()
      val stdIn = TestProbe()
      val env = Env(usercomsRep = usercomsRep.ref, scriptsRep = scriptsRep.ref)
      val target = system.actorOf(PmCode.props(Vector("a"), env, stdIn.ref, stdOut.ref))

      probe.send(target, PmCode.Commands.Exec)
      scriptsRep.expectMsg(ScriptsRep.Commands.GetScript("a.js"))
      scriptsRep.reply(ScriptsRep.Responses.Script("code"))
      probe.expectMsg(Pm.Internals.Complete(0, "code"))
    }

    "Return error if script name option is not presented" in {
      val probe = TestProbe()
      val usercomsRep = TestProbe()
      val scriptsRep = TestProbe()
      val stdOut = TestProbe()
      val stdIn = TestProbe()
      val env = Env(usercomsRep = usercomsRep.ref, scriptsRep = scriptsRep.ref)
      val target = system.actorOf(PmCode.props(Vector.empty, env, stdIn.ref, stdOut.ref))

      probe.send(target, PmCode.Commands.Exec)
      probe.expectMsg(Pm.Internals.Complete(40, "Command name is not presented"))
    }

    "Return error if name contain restricted symbols" in {
      val probe = TestProbe()
      val usercomsRep = TestProbe()
      val scriptsRep = TestProbe()
      val stdOut = TestProbe()
      val stdIn = TestProbe()
      val env = Env(usercomsRep = usercomsRep.ref, scriptsRep = scriptsRep.ref)

      val target1 = system.actorOf(PmCode.props(Vector("a b"), env, stdIn.ref, stdOut.ref))
      probe.send(target1, PmCode.Commands.Exec)
      probe.expectMsg(Pm.Internals.Complete(41, "Script name contain restricted symbols"))

      val target2 = system.actorOf(PmCode.props(Vector("..a"), env, stdIn.ref, stdOut.ref))
      probe.send(target2, PmCode.Commands.Exec)
      probe.expectMsg(Pm.Internals.Complete(41, "Script name contain restricted symbols"))

      val target3 = system.actorOf(PmCode.props(Vector("a/"), env, stdIn.ref, stdOut.ref))
      probe.send(target3, PmCode.Commands.Exec)
      probe.expectMsg(Pm.Internals.Complete(41, "Script name contain restricted symbols"))
    }

    "Return error if command does not exist in the system" in {
      val probe = TestProbe()
      val usercomsRep = TestProbe()
      val scriptsRep = TestProbe()
      val stdOut = TestProbe()
      val stdIn = TestProbe()
      val env = Env(usercomsRep = usercomsRep.ref, scriptsRep = scriptsRep.ref)
      val target = system.actorOf(PmCode.props(Vector("a"), env, stdIn.ref, stdOut.ref))

      probe.send(target, PmCode.Commands.Exec)
      scriptsRep.expectMsg(ScriptsRep.Commands.GetScript("a.js"))
      scriptsRep.reply(ScriptsRep.Responses.ScriptNotFound)
      probe.expectMsg(Pm.Internals.Complete(42, "Command does not exist"))
    }

    "Return error if usercoms repository does not respond with expected timeout" in {
      val probe = TestProbe()
      val usercomsRep = TestProbe()
      val scriptsRep = TestProbe()
      val stdOut = TestProbe()
      val stdIn = TestProbe()
      val env = Env(usercomsRep = usercomsRep.ref, scriptsRep = scriptsRep.ref)
      val target = system.actorOf(PmCode.props(Vector("a"), env, stdIn.ref, stdOut.ref, testMode = true))

      probe.send(target, PmCode.Commands.Exec)
      scriptsRep.expectMsg(ScriptsRep.Commands.GetScript("a.js"))
      probe.expectMsg(Pm.Internals.Complete(43, "Internal error type 0"))
    }
  }
}