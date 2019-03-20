package ru.serbis.okto.node.unit.syscoms.access

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import ru.serbis.okto.node.access.AccessRep
import ru.serbis.okto.node.access.AccessRep.Definitions.{GroupDefinition, UserDefinition}
import ru.serbis.okto.node.common.Env
import ru.serbis.okto.node.log.{StdOutLogger, StreamLogger}
import ru.serbis.okto.node.syscoms.access.{Access, GroupList}

class GroupListSpec extends TestKit(ActorSystem("TestSystem")) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll with StreamLogger {


  override protected def beforeAll(): Unit = {
    initializeGlobalLogger(system)
    logger.addDestination(system.actorOf(StdOutLogger.props))
  }


  "GroupList" must {
    "Process positive test" in {
      val probe = TestProbe()
      val accessRep = TestProbe()
      val stdOut = TestProbe()
      val stdIn = TestProbe()
      val env = Env(accessRep = accessRep.ref)
      val target = system.actorOf(GroupList.props(Vector.empty, env, stdIn.ref, stdOut.ref, testMode = true))

      probe.send(target, GroupList.Commands.Exec)
      accessRep.expectMsg(AccessRep.Commands.GetAccessConfig)
      accessRep.reply(AccessRep.Definitions.AccessConfig(
        Vector(
          UserDefinition("a1", "b1", "xxx", Vector("q1", "w1", "e1"),  Vector("x1", "y1", "z1")),
          UserDefinition("a2", "b2", "xxx", Vector("q2", "w2", "e2"),  Vector("x2", "y2", "z2")),
          UserDefinition("a3", "b3", "xxx", Vector("q3", "w3", "e3"),  Vector("x3", "y3", "z3"))
        ),
        Vector(
          GroupDefinition("a1", Vector("q1", "w1", "e1")),
          GroupDefinition("a2", Vector("q2", "w2", "e2")),
          GroupDefinition("a3", Vector("q3", "w3", "e3"))
        )
      ))
      probe.expectMsg(Access.Internals.Complete(0, "[\n\t{\n\t\t\"name\": \"a1\",\n\t\t\"permissions\": [\"q1\", \"w1\", \"e1\"]\n\t},\n\t{\n\t\t\"name\": \"a2\",\n\t\t\"permissions\": [\"q2\", \"w2\", \"e2\"]\n\t},\n\t{\n\t\t\"name\": \"a3\",\n\t\t\"permissions\": [\"q3\", \"w3\", \"e3\"]\n\t}\n]"))
    }

    "Return error if Access repository does not respond with expected timeout" in {
      val probe = TestProbe()
      val accessRep = TestProbe()
      val stdOut = TestProbe()
      val stdIn = TestProbe()
      val env = Env(accessRep = accessRep.ref)
      val target = system.actorOf(GroupList.props(Vector.empty, env, stdIn.ref, stdOut.ref, testMode = true))

      probe.send(target, GroupList.Commands.Exec)
      probe.expectMsg(Access.Internals.Complete(81, "Repository response timeout"))
    }
  }
}