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
        "a" -> Some(UserCommandDefinition("a", "a.js", Vector("userA1", "userA2"), Vector("groupA1", "groupA2"))),
        "b" -> Some(UserCommandDefinition("b", "b.js", Vector("userB1", "userB2"), Vector("groupB1", "groupB2"))),
        "c" -> Some(UserCommandDefinition("c", "c.js", Vector("userC1", "userC2"), Vector("groupC1", "groupC2")))
      )))
      probe.expectMsg(Pm.Internals.Complete(0, "[\n\t{\n\t\t\"name\": \"a\",\n\t\t\"users\": [\"userA1\", \"userA2\"],\n\t\t\"groups\": [\"groupA1\", \"groupA2\"]\n\t},\n\t{\n\t\t\"name\": \"b\",\n\t\t\"users\": [\"userB1\", \"userB2\"],\n\t\t\"groups\": [\"groupB1\", \"groupB2\"]\n\t},\n\t{\n\t\t\"name\": \"c\",\n\t\t\"users\": [\"userC1\", \"userC2\"],\n\t\t\"groups\": [\"groupC1\", \"groupC2\"]\n\t}\n]"))
    }

    "Process positive test - return single value if name presented" in {
      val probe = TestProbe()
      val usercomsRep = TestProbe()
      val scriptsRep = TestProbe()
      val stdOut = TestProbe()
      val stdIn = TestProbe()
      val env = Env(usercomsRep = usercomsRep.ref, scriptsRep = scriptsRep.ref)
      val target = system.actorOf(PmList.props(Vector("-name", "c"), env, stdIn.ref, stdOut.ref))

      probe.send(target, PmList.Commands.Exec)
      usercomsRep.expectMsg(UsercomsRep.Commands.GetCommandsBatch(List.empty))
      usercomsRep.reply(UsercomsRep.Responses.CommandsBatch(Map(
        "a" -> Some(UserCommandDefinition("a", "a.js", Vector("userA1", "userA2"), Vector("groupA1", "groupA2"))),
        "b" -> Some(UserCommandDefinition("b", "b.js", Vector("userB1", "userB2"), Vector("groupB1", "groupB2"))),
        "c" -> Some(UserCommandDefinition("c", "c.js", Vector("userC1", "userC2"), Vector("groupC1", "groupC2")))
      )))
      probe.expectMsg(Pm.Internals.Complete(0, "[\n\t{\n\t\t\"name\": \"c\",\n\t\t\"users\": [\"userC1\", \"userC2\"],\n\t\t\"groups\": [\"groupC1\", \"groupC2\"]\n\t}\n]"))
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