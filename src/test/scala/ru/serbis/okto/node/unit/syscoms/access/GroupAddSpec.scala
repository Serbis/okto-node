package ru.serbis.okto.node.unit.syscoms.access

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import ru.serbis.okto.node.access.AccessRep
import ru.serbis.okto.node.common.Env
import ru.serbis.okto.node.log.{StdOutLogger, StreamLogger}
import ru.serbis.okto.node.syscoms.access.{Access, GroupAdd}

class GroupAddSpec extends TestKit(ActorSystem("TestSystem")) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll with StreamLogger {


  override protected def beforeAll(): Unit = {
    initializeGlobalLogger(system)
    logger.addDestination(system.actorOf(StdOutLogger.props))
  }


  "GroupAdd" must {
    "Process positive test" in {
      val probe = TestProbe()
      val accessRep = TestProbe()
      val stdOut = TestProbe()
      val stdIn = TestProbe()
      val env = Env(accessRep = accessRep.ref)
      val target = system.actorOf(GroupAdd.props(Vector("name=a", "permissions=q,w,e"), env, stdIn.ref, stdOut.ref))

      probe.send(target, GroupAdd.Commands.Exec)
      accessRep.expectMsg(AccessRep.Commands.AddGroup("a", Vector("q", "w", "e")))
      accessRep.reply(AccessRep.Responses.Success)
      probe.expectMsg(Access.Internals.Complete(0, "OK"))
    }

    "Return error if name contains restricted symbols" in {
      val probe = TestProbe()
      val accessRep = TestProbe()
      val stdOut = TestProbe()
      val stdIn = TestProbe()
      val env = Env(accessRep = accessRep.ref)
      val target = system.actorOf(GroupAdd.props(Vector("name=a~", "permissions=q,w,e"), env, stdIn.ref, stdOut.ref))

      probe.send(target, GroupAdd.Commands.Exec)
      probe.expectMsg(Access.Internals.Complete(26, "Name contains restricted symbols"))
    }

    "Return error if args does not presented" in {
      val probe = TestProbe()
      val AccessRep = TestProbe()
      val stdOut = TestProbe()
      val stdIn = TestProbe()
      val env = Env(accessRep = AccessRep.ref)
      val target = system.actorOf(GroupAdd.props(Vector.empty, env, stdIn.ref, stdOut.ref))

      probe.send(target, GroupAdd.Commands.Exec)

      probe.expectMsg(Access.Internals.Complete(20, "Required mandatory args"))
    }

    "Return error if repository respond with Exist" in {
      val probe = TestProbe()
      val accessRep = TestProbe()
      val stdOut = TestProbe()
      val stdIn = TestProbe()
      val env = Env(accessRep = accessRep.ref)
      val target = system.actorOf(GroupAdd.props(Vector("name=a", "permissions=q,w,e"), env, stdIn.ref, stdOut.ref))

      probe.send(target, GroupAdd.Commands.Exec)
      accessRep.expectMsg(AccessRep.Commands.AddGroup("a", Vector("q", "w", "e")))
      accessRep.reply(AccessRep.Responses.Exist)
      probe.expectMsg(Access.Internals.Complete(21, "Group already exists"))
    }

    "Return error if repository respond with UnknownPermission" in {
      val probe = TestProbe()
      val accessRep = TestProbe()
      val stdOut = TestProbe()
      val stdIn = TestProbe()
      val env = Env(accessRep = accessRep.ref)
      val target = system.actorOf(GroupAdd.props(Vector("name=a", "permissions=q,w,e"), env, stdIn.ref, stdOut.ref))

      probe.send(target, GroupAdd.Commands.Exec)
      accessRep.expectMsg(AccessRep.Commands.AddGroup("a", Vector("q", "w", "e")))
      accessRep.reply(AccessRep.Responses.UnknownPermission("w"))
      probe.expectMsg(Access.Internals.Complete(23, "Permission 'w' is unknown"))
    }

    "Return error if repository respond with WriteError" in {
      val probe = TestProbe()
      val accessRep = TestProbe()
      val stdOut = TestProbe()
      val stdIn = TestProbe()
      val env = Env(accessRep = accessRep.ref)
      val target = system.actorOf(GroupAdd.props(Vector("name=a", "permissions=q,w,e"), env, stdIn.ref, stdOut.ref))

      probe.send(target, GroupAdd.Commands.Exec)
      accessRep.expectMsg(AccessRep.Commands.AddGroup("a", Vector("q", "w", "e")))
      accessRep.reply(AccessRep.Responses.WriteError(new Exception("x")))
      probe.expectMsg(Access.Internals.Complete(24, "Configuration io error"))
    }

    "Return error if Access repository does not respond with expected timeout" in {
      val probe = TestProbe()
      val accessRep = TestProbe()
      val stdOut = TestProbe()
      val stdIn = TestProbe()
      val env = Env(accessRep = accessRep.ref)
      val target = system.actorOf(GroupAdd.props(Vector("name=a", "permissions=q,w,e"), env, stdIn.ref, stdOut.ref, testMode = true))

      probe.send(target, GroupAdd.Commands.Exec)
      probe.expectMsg(Access.Internals.Complete(25, "Repository response timeout"))
    }
  }
}