package ru.serbis.okto.node.unit.syscoms.boot

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import ru.serbis.okto.node.common.Env
import ru.serbis.okto.node.log.{StdOutLogger, StreamLogger}
import ru.serbis.okto.node.reps.BootRep
import ru.serbis.okto.node.syscoms.boot._

class BootInfoSpec extends TestKit(ActorSystem("TestSystem")) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll with StreamLogger {


  override protected def beforeAll(): Unit = {
    initializeGlobalLogger(system)
    logger.addDestination(system.actorOf(StdOutLogger.props))
  }


  "BootInfo" must {
    "Process positive test" in {
      val probe = TestProbe()
      val bootRep = TestProbe()
      val stdOut = TestProbe()
      val stdIn = TestProbe()
      val env = Env(bootRep = bootRep.ref)
      val target = system.actorOf(BootInfo.props(Vector.empty, env, stdIn.ref, stdOut.ref))

      probe.send(target, BootInfo.Commands.Exec)
      bootRep.expectMsg(BootRep.Commands.GetAll)
      bootRep.reply(List(
        BootRep.Responses.BootDefinition(1, "cmd 1"),
        BootRep.Responses.BootDefinition(2, "cmd 2"),
        BootRep.Responses.BootDefinition(3, "cmd 3")
      ))
      probe.expectMsg(Boot.Internals.Complete(0, "[\n\t{\n\t\t\"id\": 1,\n\t\t\"cmd\": \"cmd 1\"\n\t},\n\t{\n\t\t\"id\": 2,\n\t\t\"cmd\": \"cmd 2\"\n\t},\n\t{\n\t\t\"id\": 3,\n\t\t\"cmd\": \"cmd 3\"\n\t}\n]"))
    }

    "Return error if boot repository does not respond with expected timeout" in {
      val probe = TestProbe()
      val bootRep = TestProbe()
      val stdOut = TestProbe()
      val stdIn = TestProbe()
      val env = Env(bootRep = bootRep.ref)
      val target = system.actorOf(BootInfo.props(Vector.empty, env, stdIn.ref, stdOut.ref, testMode = true))

      probe.send(target, BootInfo.Commands.Exec)
      bootRep.expectMsg(BootRep.Commands.GetAll)
      probe.expectMsg(Boot.Internals.Complete(10, "Repository timeout"))
    }
  }
}