package ru.serbis.okto.node.unit.syscoms.access

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import ru.serbis.okto.node.access.AccessRep
import ru.serbis.okto.node.common.Env
import ru.serbis.okto.node.log.{StdOutLogger, StreamLogger}
import ru.serbis.okto.node.syscoms.access.{Access, UserAdd}

class UserAddSpec extends TestKit(ActorSystem("TestSystem")) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll with StreamLogger {


  override protected def beforeAll(): Unit = {
    initializeGlobalLogger(system)
    logger.addDestination(system.actorOf(StdOutLogger.props))
  }


  "UserAdd" must {
    "Process positive test" in {
      val probe = TestProbe()
      val accessRep = TestProbe()
      val stdOut = TestProbe()
      val stdIn = TestProbe()
      val env = Env(accessRep = accessRep.ref)
      val target = system.actorOf(UserAdd.props(Vector("name=a", "password=b", "permissions=q,w,e", "groups=x,y,z"), env, stdIn.ref, stdOut.ref))

      probe.send(target, UserAdd.Commands.Exec)
      accessRep.expectMsg(AccessRep.Commands.AddUser("a", "b", Vector("q", "w", "e"), Vector("x", "y", "z")))
      accessRep.reply(AccessRep.Responses.Success)
      probe.expectMsg(Access.Internals.Complete(0, "OK"))
    }

    "Return error if user name contains restricted symbols" in {
      val probe = TestProbe()
      val accessRep = TestProbe()
      val stdOut = TestProbe()
      val stdIn = TestProbe()
      val env = Env(accessRep = accessRep.ref)
      val target = system.actorOf(UserAdd.props(Vector("name=a~", "password=b", "permissions=q,w,e", "groups=x,y,z"), env, stdIn.ref, stdOut.ref))

      probe.send(target, UserAdd.Commands.Exec)
      probe.expectMsg(Access.Internals.Complete(16, "Name contains restricted symbols"))
    }

    "Return error if args does not presented" in {
      val probe = TestProbe()
      val AccessRep = TestProbe()
      val stdOut = TestProbe()
      val stdIn = TestProbe()
      val env = Env(accessRep = AccessRep.ref)
      val target = system.actorOf(UserAdd.props(Vector.empty, env, stdIn.ref, stdOut.ref))

      probe.send(target, UserAdd.Commands.Exec)

      probe.expectMsg(Access.Internals.Complete(10, "Required mandatory args"))
    }

    "Return error if repository respond with Exist" in {
      val probe = TestProbe()
      val accessRep = TestProbe()
      val stdOut = TestProbe()
      val stdIn = TestProbe()
      val env = Env(accessRep = accessRep.ref)
      val target = system.actorOf(UserAdd.props(Vector("name=a", "password=b", "permissions=q,w,e", "groups=x,y,z"), env, stdIn.ref, stdOut.ref))

      probe.send(target, UserAdd.Commands.Exec)
      accessRep.expectMsg(AccessRep.Commands.AddUser("a", "b", Vector("q", "w", "e"), Vector("x", "y", "z")))
      accessRep.reply(AccessRep.Responses.Exist)
      probe.expectMsg(Access.Internals.Complete(11, "User already exists"))
    }

    "Return error if repository respond with GroupNotExit" in {
      val probe = TestProbe()
      val accessRep = TestProbe()
      val stdOut = TestProbe()
      val stdIn = TestProbe()
      val env = Env(accessRep = accessRep.ref)
      val target = system.actorOf(UserAdd.props(Vector("name=a", "password=b", "permissions=q,w,e", "groups=x,y,z"), env, stdIn.ref, stdOut.ref))

      probe.send(target, UserAdd.Commands.Exec)
      accessRep.expectMsg(AccessRep.Commands.AddUser("a", "b", Vector("q", "w", "e"), Vector("x", "y", "z")))
      accessRep.reply(AccessRep.Responses.GroupNotExist("y"))
      probe.expectMsg(Access.Internals.Complete(12, "Group 'y' does not exist"))
    }

    "Return error if repository respond with UnknownPermission" in {
      val probe = TestProbe()
      val accessRep = TestProbe()
      val stdOut = TestProbe()
      val stdIn = TestProbe()
      val env = Env(accessRep = accessRep.ref)
      val target = system.actorOf(UserAdd.props(Vector("name=a", "password=b", "permissions=q,w,e", "groups=x,y,z"), env, stdIn.ref, stdOut.ref))

      probe.send(target, UserAdd.Commands.Exec)
      accessRep.expectMsg(AccessRep.Commands.AddUser("a", "b", Vector("q", "w", "e"), Vector("x", "y", "z")))
      accessRep.reply(AccessRep.Responses.UnknownPermission("w"))
      probe.expectMsg(Access.Internals.Complete(13, "Permission 'w' is unknown"))
    }

    "Return error if repository respond with WriteError" in {
      val probe = TestProbe()
      val accessRep = TestProbe()
      val stdOut = TestProbe()
      val stdIn = TestProbe()
      val env = Env(accessRep = accessRep.ref)
      val target = system.actorOf(UserAdd.props(Vector("name=a", "password=b", "permissions=q,w,e", "groups=x,y,z"), env, stdIn.ref, stdOut.ref))

      probe.send(target, UserAdd.Commands.Exec)
      accessRep.expectMsg(AccessRep.Commands.AddUser("a", "b", Vector("q", "w", "e"), Vector("x", "y", "z")))
      accessRep.reply(AccessRep.Responses.WriteError(new Exception("x")))
      probe.expectMsg(Access.Internals.Complete(14, "Configuration io error"))
    }

    "Return error if Access repository does not respond with expected timeout" in {
      val probe = TestProbe()
      val accessRep = TestProbe()
      val stdOut = TestProbe()
      val stdIn = TestProbe()
      val env = Env(accessRep = accessRep.ref)
      val target = system.actorOf(UserAdd.props(Vector("name=a", "password=b", "permissions=q,w,e", "groups=x,y,z"), env, stdIn.ref, stdOut.ref, testMode = true))

      probe.send(target, UserAdd.Commands.Exec)
      probe.expectMsg(Access.Internals.Complete(15, "Repository response timeout"))
    }
  }
}