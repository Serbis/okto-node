package ru.serbis.okto.node.unit.syscoms.boot

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import ru.serbis.okto.node.common.Env
import ru.serbis.okto.node.log.{StdOutLogger, StreamLogger}
import ru.serbis.okto.node.reps.BootRep
import ru.serbis.okto.node.syscoms.boot._

class BootRemoveSpec extends TestKit(ActorSystem("TestSystem")) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll with StreamLogger {


  override protected def beforeAll(): Unit = {
    initializeGlobalLogger(system)
    logger.addDestination(system.actorOf(StdOutLogger.props))
  }


  "BootRemove" must {
    "Process positive test" in {
      val probe = TestProbe()
      val bootRep = TestProbe()
      val stdOut = TestProbe()
      val stdIn = TestProbe()
      val env = Env(bootRep = bootRep.ref)
      val target = system.actorOf(BootRemove.props(Vector("1"), env, stdIn.ref, stdOut.ref))

      probe.send(target, BootRemove.Commands.Exec)
      bootRep.expectMsg(BootRep.Commands.Remove(1))
      bootRep.reply(BootRep.Responses.Removed)
      probe.expectMsg(Boot.Internals.Complete(0, "OK"))
    }

    "Return error if args does not presented" in {
      val probe = TestProbe()
      val bootRep = TestProbe()
      val stdOut = TestProbe()
      val stdIn = TestProbe()
      val env = Env(bootRep = bootRep.ref)
      val target = system.actorOf(BootRemove.props(Vector.empty, env, stdIn.ref, stdOut.ref))

      probe.send(target, BootRemove.Commands.Exec)
      probe.expectMsg(Boot.Internals.Complete(30, "Required boot entry id"))
    }

    "Return error if args does has bad type" in {
      val probe = TestProbe()
      val bootRep = TestProbe()
      val stdOut = TestProbe()
      val stdIn = TestProbe()
      val env = Env(bootRep = bootRep.ref)
      val target = system.actorOf(BootRemove.props(Vector("x"), env, stdIn.ref, stdOut.ref))

      probe.send(target, BootRemove.Commands.Exec)
      probe.expectMsg(Boot.Internals.Complete(31, "Boot entry id must be an integer"))
    }

    "Return error if repository respond with error" in {
      val probe = TestProbe()
      val bootRep = TestProbe()
      val stdOut = TestProbe()
      val stdIn = TestProbe()
      val env = Env(bootRep = bootRep.ref)
      val target = system.actorOf(BootRemove.props(Vector("1"), env, stdIn.ref, stdOut.ref))

      probe.send(target, BootRemove.Commands.Exec)
      bootRep.expectMsg(BootRep.Commands.Remove(1))
      bootRep.reply(BootRep.Responses.WriteError)
      probe.expectMsg(Boot.Internals.Complete(32, "Configuration io error"))
    }

    "Return error if repository respond with not exsit mesage" in {
      val probe = TestProbe()
      val bootRep = TestProbe()
      val stdOut = TestProbe()
      val stdIn = TestProbe()
      val env = Env(bootRep = bootRep.ref)
      val target = system.actorOf(BootRemove.props(Vector("1"), env, stdIn.ref, stdOut.ref))

      probe.send(target, BootRemove.Commands.Exec)
      bootRep.expectMsg(BootRep.Commands.Remove(1))
      bootRep.reply(BootRep.Responses.NotExist)
      probe.expectMsg(Boot.Internals.Complete(34, "Boot entry '1' does not exist"))
    }

    "Return error if boot repository does not respond with expected timeout" in {
      val probe = TestProbe()
      val bootRep = TestProbe()
      val stdOut = TestProbe()
      val stdIn = TestProbe()
      val env = Env(bootRep = bootRep.ref)
      val target = system.actorOf(BootRemove.props(Vector("1"), env, stdIn.ref, stdOut.ref, testMode = true))

      probe.send(target, BootRemove.Commands.Exec)
      bootRep.expectMsg(BootRep.Commands.Remove(1))
      probe.expectMsg(Boot.Internals.Complete(33, "Repository response timeout"))
    }
  }
}