package ru.serbis.okto.node.unit.syscoms.access

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import ru.serbis.okto.node.access.AccessRep
import ru.serbis.okto.node.access.AccessRep.Definitions.UserDefinition
import ru.serbis.okto.node.common.Env
import ru.serbis.okto.node.log.{StdOutLogger, StreamLogger}
import ru.serbis.okto.node.syscoms.access.{Access, UserInfo}

class UserInfoSpec extends TestKit(ActorSystem("TestSystem")) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll with StreamLogger {


  override protected def beforeAll(): Unit = {
    initializeGlobalLogger(system)
    logger.addDestination(system.actorOf(StdOutLogger.props))
  }


  "UserInfo" must {
    "Process positive test" in {
      val probe = TestProbe()
      val accessRep = TestProbe()
      val stdOut = TestProbe()
      val stdIn = TestProbe()
      val env = Env(accessRep = accessRep.ref)
      val target = system.actorOf(UserInfo.props(Vector("name=a2"), env, stdIn.ref, stdOut.ref))

      probe.send(target, UserInfo.Commands.Exec)
      accessRep.expectMsg(AccessRep.Commands.GetAccessConfig)
      accessRep.reply(AccessRep.Definitions.AccessConfig(
        Vector(
          UserDefinition("a1", "b1", "xxx", Vector("q1", "w1", "e1"),  Vector("x1", "y1", "z1")),
          UserDefinition("a2", "b2", "xxx", Vector("q2", "w2", "e2"),  Vector("x2", "y2", "z2")),
          UserDefinition("a3", "b3", "xxx", Vector("q3", "w3", "e3"),  Vector("x3", "y3", "z3"))
        ),
        Vector.empty
      ))
      probe.expectMsg(Access.Internals.Complete(0, "{\n\t\"name\": \"a2\",\n\t\"password\": \"***\",\n\t\"permissions\": [\"q2\", \"w2\", \"e2\"],\n\t\"groups\": [\"x2\", \"y2\", \"z2\"]\n}"))
    }

    "Return error if user does not exist in the returned access config" in {
      val probe = TestProbe()
      val accessRep = TestProbe()
      val stdOut = TestProbe()
      val stdIn = TestProbe()
      val env = Env(accessRep = accessRep.ref)
      val target = system.actorOf(UserInfo.props(Vector("name=a4"), env, stdIn.ref, stdOut.ref))

      probe.send(target, UserInfo.Commands.Exec)
      accessRep.expectMsg(AccessRep.Commands.GetAccessConfig)
      accessRep.reply(AccessRep.Definitions.AccessConfig(
        Vector(
          UserDefinition("a1", "b1", "xxx", Vector("q1", "w1", "e1"),  Vector("x1", "y1", "z1")),
          UserDefinition("a2", "b2", "xxx", Vector("q2", "w2", "e2"),  Vector("x2", "y2", "z2")),
          UserDefinition("a3", "b3", "xxx", Vector("q3", "w3", "e3"),  Vector("x3", "y3", "z3"))
        ),
        Vector.empty
      ))
      probe.expectMsg(Access.Internals.Complete(51,"User does not exist"))
    }

    "Return error if args does not presented" in {
      val probe = TestProbe()
      val AccessRep = TestProbe()
      val stdOut = TestProbe()
      val stdIn = TestProbe()
      val env = Env(accessRep = AccessRep.ref)
      val target = system.actorOf(UserInfo.props(Vector.empty, env, stdIn.ref, stdOut.ref))

      probe.send(target, UserInfo.Commands.Exec)

      probe.expectMsg(Access.Internals.Complete(50, "Required mandatory args"))
    }

    "Return error if Access repository does not respond with expected timeout" in {
      val probe = TestProbe()
      val accessRep = TestProbe()
      val stdOut = TestProbe()
      val stdIn = TestProbe()
      val env = Env(accessRep = accessRep.ref)
      val target = system.actorOf(UserInfo.props(Vector("name=a"), env, stdIn.ref, stdOut.ref, testMode = true))

      probe.send(target, UserInfo.Commands.Exec)
      probe.expectMsg(Access.Internals.Complete(52, "Repository response timeout"))
    }
  }
}