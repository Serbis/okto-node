package ru.serbis.okto.node.unit.syscoms.boot

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import ru.serbis.okto.node.common.Env
import ru.serbis.okto.node.log.{StdOutLogger, StreamLogger}
import ru.serbis.okto.node.reps.BootRep
import ru.serbis.okto.node.syscoms.boot._

class BootAddSpec extends TestKit(ActorSystem("TestSystem")) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll with StreamLogger {


  override protected def beforeAll(): Unit = {
    initializeGlobalLogger(system)
    logger.addDestination(system.actorOf(StdOutLogger.props))
  }


  "BootAdd" must {
    "Process positive test" in {
      val probe = TestProbe()
      val bootRep = TestProbe()
      val stdOut = TestProbe()
      val stdIn = TestProbe()
      val env = Env(bootRep = bootRep.ref)
      val target = system.actorOf(BootAdd.props(Vector("-c", "x"), env, stdIn.ref, stdOut.ref))

      probe.send(target, BootAdd.Commands.Exec)
      bootRep.expectMsg(BootRep.Commands.Create("x"))
      bootRep.reply(BootRep.Responses.BootDefinition(1, "x"))
      probe.expectMsg(Boot.Internals.Complete(0, "1"))
    }

    "Return error if args does not presented" in {
      val probe = TestProbe()
      val bootRep = TestProbe()
      val stdOut = TestProbe()
      val stdIn = TestProbe()
      val env = Env(bootRep = bootRep.ref)
      val target = system.actorOf(BootAdd.props(Vector.empty, env, stdIn.ref, stdOut.ref))

      probe.send(target, BootAdd.Commands.Exec)

      probe.expectMsg(Boot.Internals.Complete(20, "Required -c arg"))
    }

    "Return error if repository respond with error" in {
      val probe = TestProbe()
      val bootRep = TestProbe()
      val stdOut = TestProbe()
      val stdIn = TestProbe()
      val env = Env(bootRep = bootRep.ref)
      val target = system.actorOf(BootAdd.props(Vector("-c", "x"), env, stdIn.ref, stdOut.ref))

      probe.send(target, BootAdd.Commands.Exec)
      bootRep.expectMsg(BootRep.Commands.Create("x"))
      bootRep.reply(BootRep.Responses.WriteError)
      probe.expectMsg(Boot.Internals.Complete(21, "Configuration io error"))
    }

    "Return error if boot repository does not respond with expected timeout" in {
      val probe = TestProbe()
      val bootRep = TestProbe()
      val stdOut = TestProbe()
      val stdIn = TestProbe()
      val env = Env(bootRep = bootRep.ref)
      val target = system.actorOf(BootAdd.props(Vector("-c", "x"), env, stdIn.ref, stdOut.ref, testMode = true))

      probe.send(target, BootAdd.Commands.Exec)
      bootRep.expectMsg(BootRep.Commands.Create("x"))
      probe.expectMsg(Boot.Internals.Complete(22, "Repository response timeout"))
    }
  }
}