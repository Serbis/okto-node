package ru.serbis.okto.node.unit.syscoms.access

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import ru.serbis.okto.node.access.AccessRep
import ru.serbis.okto.node.access.AccessRep.Definitions.UserDefinition
import ru.serbis.okto.node.common.Env
import ru.serbis.okto.node.log.{StdOutLogger, StreamLogger}
import ru.serbis.okto.node.syscoms.access.{Access, UserList}

class UserListSpec extends TestKit(ActorSystem("TestSystem")) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll with StreamLogger {


  override protected def beforeAll(): Unit = {
    initializeGlobalLogger(system)
    logger.addDestination(system.actorOf(StdOutLogger.props))
  }


  "UserList" must {
    "Process positive test" in {
      val probe = TestProbe()
      val accessRep = TestProbe()
      val stdOut = TestProbe()
      val stdIn = TestProbe()
      val env = Env(accessRep = accessRep.ref)
      val target = system.actorOf(UserList.props(Vector.empty, env, stdIn.ref, stdOut.ref, testMode = true))

      probe.send(target, UserList.Commands.Exec)
      accessRep.expectMsg(AccessRep.Commands.GetAccessConfig)
      accessRep.reply(AccessRep.Definitions.AccessConfig(
        Vector(
          UserDefinition("a1", "b1", "xxx", Vector("q1", "w1", "e1"),  Vector("x1", "y1", "z1")),
          UserDefinition("a2", "b2", "xxx", Vector("q2", "w2", "e2"),  Vector("x2", "y2", "z2")),
          UserDefinition("a3", "b3", "xxx", Vector("q3", "w3", "e3"),  Vector("x3", "y3", "z3"))
        ),
        Vector.empty
      ))
      probe.expectMsg(Access.Internals.Complete(0, "[\n\t{\n\t\t\"name\": \"a1\",\n\t\t\"password\": \"***\",\n\t\t\"permissions\": [\"q1\", \"w1\", \"e1\"],\n\t\t\"groups\": [\"x1\", \"y1\", \"z1\"]\n\t},\n\t{\n\t\t\"name\": \"a2\",\n\t\t\"password\": \"***\",\n\t\t\"permissions\": [\"q2\", \"w2\", \"e2\"],\n\t\t\"groups\": [\"x2\", \"y2\", \"z2\"]\n\t},\n\t{\n\t\t\"name\": \"a3\",\n\t\t\"password\": \"***\",\n\t\t\"permissions\": [\"q3\", \"w3\", \"e3\"],\n\t\t\"groups\": [\"x3\", \"y3\", \"z3\"]\n\t}\n]"))
    }

    "Return error if Access repository does not respond with expected timeout" in {
      val probe = TestProbe()
      val accessRep = TestProbe()
      val stdOut = TestProbe()
      val stdIn = TestProbe()
      val env = Env(accessRep = accessRep.ref)
      val target = system.actorOf(UserList.props(Vector.empty, env, stdIn.ref, stdOut.ref, testMode = true))

      probe.send(target, UserList.Commands.Exec)
      probe.expectMsg(Access.Internals.Complete(71, "Repository response timeout"))
    }
  }
}