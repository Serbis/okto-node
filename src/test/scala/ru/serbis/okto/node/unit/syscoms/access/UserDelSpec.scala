package ru.serbis.okto.node.unit.syscoms.access

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import ru.serbis.okto.node.access.AccessRep
import ru.serbis.okto.node.common.Env
import ru.serbis.okto.node.log.{StdOutLogger, StreamLogger}
import ru.serbis.okto.node.syscoms.access.{Access, UserDel}

class UserDelSpec extends TestKit(ActorSystem("TestSystem")) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll with StreamLogger {


  override protected def beforeAll(): Unit = {
    initializeGlobalLogger(system)
    logger.addDestination(system.actorOf(StdOutLogger.props))
  }


  "UserDel" must {
    "Process positive test" in {
      val probe = TestProbe()
      val accessRep = TestProbe()
      val stdOut = TestProbe()
      val stdIn = TestProbe()
      val env = Env(accessRep = accessRep.ref)
      val target = system.actorOf(UserDel.props(Vector("name=a"), env, stdIn.ref, stdOut.ref))

      probe.send(target, UserDel.Commands.Exec)
      accessRep.expectMsg(AccessRep.Commands.DelUser("a"))
      accessRep.reply(AccessRep.Responses.Success)
      probe.expectMsg(Access.Internals.Complete(0, "OK"))
    }

    "Return error if args does not presented" in {
      val probe = TestProbe()
      val AccessRep = TestProbe()
      val stdOut = TestProbe()
      val stdIn = TestProbe()
      val env = Env(accessRep = AccessRep.ref)
      val target = system.actorOf(UserDel.props(Vector.empty, env, stdIn.ref, stdOut.ref))

      probe.send(target, UserDel.Commands.Exec)

      probe.expectMsg(Access.Internals.Complete(30, "Required mandatory args"))
    }

    "Return error if repository respond with NotExist" in {
      val probe = TestProbe()
      val accessRep = TestProbe()
      val stdOut = TestProbe()
      val stdIn = TestProbe()
      val env = Env(accessRep = accessRep.ref)
      val target = system.actorOf(UserDel.props(Vector("name=a"), env, stdIn.ref, stdOut.ref))

      probe.send(target, UserDel.Commands.Exec)
      accessRep.expectMsg(AccessRep.Commands.DelUser("a"))
      accessRep.reply(AccessRep.Responses.NotExist)
      probe.expectMsg(Access.Internals.Complete(31, "User does not exists"))
    }


    "Return error if repository respond with WriteError" in {
      val probe = TestProbe()
      val accessRep = TestProbe()
      val stdOut = TestProbe()
      val stdIn = TestProbe()
      val env = Env(accessRep = accessRep.ref)
      val target = system.actorOf(UserDel.props(Vector("name=a"), env, stdIn.ref, stdOut.ref))

      probe.send(target, UserDel.Commands.Exec)
      accessRep.expectMsg(AccessRep.Commands.DelUser("a"))
      accessRep.reply(AccessRep.Responses.WriteError(new Exception("x")))
      probe.expectMsg(Access.Internals.Complete(32, "Configuration io error"))
    }

    "Return error if Access repository does not respond with expected timeout" in {
      val probe = TestProbe()
      val accessRep = TestProbe()
      val stdOut = TestProbe()
      val stdIn = TestProbe()
      val env = Env(accessRep = accessRep.ref)
      val target = system.actorOf(UserDel.props(Vector("name=a"), env, stdIn.ref, stdOut.ref, testMode = true))

      probe.send(target, UserDel.Commands.Exec)
      probe.expectMsg(Access.Internals.Complete(33, "Repository response timeout"))
    }
  }
}