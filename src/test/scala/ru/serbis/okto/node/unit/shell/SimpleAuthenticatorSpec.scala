package ru.serbis.okto.node.unit.shell

import java.io.File

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import ru.serbis.okto.node.access.AccessCredentials.UserCredentials
import ru.serbis.okto.node.access.{AccessCredentials, AccessRep}
import ru.serbis.okto.node.common.Env
import ru.serbis.okto.node.log.{StdOutLogger, StreamLogger}
import ru.serbis.okto.node.syscoms.shell.{Shell, SimpleAuthenticator}

class SimpleAuthenticatorSpec extends TestKit(ActorSystem("TestSystem")) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll with StreamLogger {


  override protected def beforeAll(): Unit = {
    initializeGlobalLogger(system)
    logger.addDestination(system.actorOf(StdOutLogger.props))
  }

  "SimpleAuthenticator" must {
    "Process positive test" in {
      val probe = TestProbe()
      val accessRep = TestProbe()
      val env = Env(accessRep = accessRep.ref)
      val target = system.actorOf(SimpleAuthenticator.props(env))
      probe.send(target, SimpleAuthenticator.Commands.Exec(Vector("root", "12345")))
      accessRep.expectMsg(AccessRep.Commands.GetPermissionsDefinition("root"))
      accessRep.reply(UserCredentials("root", AccessCredentials.hashPassword("12345", "xxx"), "xxx", Set.empty, Set.empty))
      probe.expectMsg(Shell.Internals.AuthSuccess(UserCredentials("root", AccessCredentials.hashPassword("12345", "xxx"), "xxx", Set.empty, Set.empty)))
    }

    "Return InsufficientInputData if user and password pair does not presented" in {
      val probe = TestProbe()
      val accessRep = TestProbe()
      val env = Env(accessRep = accessRep.ref)
      val target = system.actorOf(SimpleAuthenticator.props(env))
      probe.send(target, SimpleAuthenticator.Commands.Exec(Vector("root")))
      probe.expectMsg(Shell.Internals.InsufficientInputData)
    }

    "Return AuthFailed if user does not exist" in {
      val probe = TestProbe()
      val accessRep = TestProbe()
      val env = Env(accessRep = accessRep.ref)
      val target = system.actorOf(SimpleAuthenticator.props(env))
      probe.send(target, SimpleAuthenticator.Commands.Exec(Vector("root", "12345")))
      accessRep.expectMsg(AccessRep.Commands.GetPermissionsDefinition("root"))
      accessRep.reply(AccessRep.Responses.NotExist)
      probe.expectMsg(Shell.Internals.AuthFailed("User does not exist"))
    }

    "Return AuthFailed if user password does not correspond to the expected" in {
      val probe = TestProbe()
      val accessRep = TestProbe()
      val env = Env(accessRep = accessRep.ref)
      val target = system.actorOf(SimpleAuthenticator.props(env))
      probe.send(target, SimpleAuthenticator.Commands.Exec(Vector("root", "12345")))
      accessRep.expectMsg(AccessRep.Commands.GetPermissionsDefinition("root"))
      accessRep.reply(UserCredentials("root", "333333", "xxx", Set.empty, Set.empty))
      probe.expectMsg(Shell.Internals.AuthFailed("Incorrect password"))
    }

    "Return AuthTimeout if repository does not respond" in {
      val probe = TestProbe()
      val accessRep = TestProbe()
      val env = Env(accessRep = accessRep.ref)
      val target = system.actorOf(SimpleAuthenticator.props(env, testMode = true))
      probe.send(target, SimpleAuthenticator.Commands.Exec(Vector("root", "12345")))
      accessRep.expectMsg(AccessRep.Commands.GetPermissionsDefinition("root"))
      probe.expectMsg(Shell.Internals.AuthTimeout)
    }
  }
}
