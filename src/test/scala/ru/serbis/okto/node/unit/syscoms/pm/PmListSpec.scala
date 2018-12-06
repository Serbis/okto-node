package ru.serbis.okto.node.unit.syscoms.pm

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import ru.serbis.okto.node.common.Env
import ru.serbis.okto.node.log.{StdOutLogger, StreamLogger}
import ru.serbis.okto.node.reps.UsercomsRep.Responses.UserCommandDefinition
import ru.serbis.okto.node.reps.{ScriptsRep, UsercomsRep}
import ru.serbis.okto.node.syscoms.pm.{Pm, PmList, PmRemove}

class PmListSpec extends TestKit(ActorSystem("TestSystem")) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll with StreamLogger {


  override protected def beforeAll(): Unit = {
    initializeGlobalLogger(system)
    logger.addDestination(system.actorOf(StdOutLogger.props))
  }


  "PmList" must {
    "Process positive test" in {
      val probe = TestProbe()
      val usercomsRep = TestProbe()
      val scriptsRep = TestProbe()
      val stdOut = TestProbe()
      val stdIn = TestProbe()
      val env = Env(usercomsRep = usercomsRep.ref, scriptsRep = scriptsRep.ref)
      val target = system.actorOf(PmList.props(Vector.empty, env, stdIn.ref, stdOut.ref))

      probe.send(target, PmList.Commands.Exec)
      usercomsRep.expectMsg(UsercomsRep.Commands.GetCommandsBatch(List.empty))
      usercomsRep.reply(UsercomsRep.Responses.CommandsBatch(Map(
        "a" -> Some(UserCommandDefinition("a", "a.js")),
        "b" -> Some(UserCommandDefinition("b", "b.js")),
        "c" -> Some(UserCommandDefinition("c", "c.js"))
      )))
      probe.expectMsg(Pm.Internals.Complete(0, "a\nb\nc"))
    }

    "Return error if usercoms repository does not respond with expected timeout" in {
      val probe = TestProbe()
      val usercomsRep = TestProbe()
      val scriptsRep = TestProbe()
      val stdOut = TestProbe()
      val stdIn = TestProbe()
      val env = Env(usercomsRep = usercomsRep.ref, scriptsRep = scriptsRep.ref)
      val target = system.actorOf(PmList.props(Vector.empty, env, stdIn.ref, stdOut.ref, testMode = true))

      probe.send(target, PmList.Commands.Exec)
      usercomsRep.expectMsg(UsercomsRep.Commands.GetCommandsBatch(List.empty))
      probe.expectMsg(Pm.Internals.Complete(30, "Internal error type 0"))
    }
  }
}