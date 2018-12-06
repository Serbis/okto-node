package ru.serbis.okto.node.unit.runtime

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import ru.serbis.okto.node.common.Env
import ru.serbis.okto.node.log.{StdOutLogger, StreamLogger}
import ru.serbis.okto.node.reps.SyscomsRep.Responses.SystemCommandDefinition
import ru.serbis.okto.node.runtime.{ProcessConstructor, Runtime}

class RuntimeSpec extends TestKit(ActorSystem("TestSystem")) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll with StreamLogger {

  override protected def beforeAll(): Unit = {
    initializeGlobalLogger(system)
    logger.addDestination(system.actorOf(StdOutLogger.props))
  }

  "Runtime" must {
    "Return unique id by ReservePid message and return empty process reference by GetProcess message" in {
      val probe = TestProbe()
      val target = system.actorOf(Runtime.props(Env()))

      probe.send(target, Runtime.Commands.ReservePid)
      val pid = probe.expectMsgType[Runtime.Responses.Pid]
      probe.send(target, Runtime.Commands.GetProcess(pid.id))
      probe.expectMsg(Runtime.Responses.Process(None))
    }

    "Return PidNotRegister by GetProcess message for not registered pid" in {
      val probe = TestProbe()
      val target = system.actorOf(Runtime.props(Env()))

      probe.send(target, Runtime.Commands.GetProcess(0))
      probe.expectMsg(Runtime.Responses.ProcessNotRegistered)
    }

    "Remove process from pool by RemoveProcess and return Process with message" in {
      val probe = TestProbe()
      val target = system.actorOf(Runtime.props(Env()))

      probe.send(target, Runtime.Commands.ReservePid)
      val pid = probe.expectMsgType[Runtime.Responses.Pid]
      probe.send(target, Runtime.Commands.RemoveProcess(pid.id))
      probe.send(target, Runtime.Commands.GetProcess(pid.id))
      probe.expectMsg(Runtime.Responses.Process(None))
      probe.send(target, Runtime.Commands.GetProcess(pid.id))
      probe.expectMsg(Runtime.Responses.ProcessNotRegistered)
    }

    "Return ProcessNotRegistered by RemoveProcess for not registered pid" in {
      val probe = TestProbe()
      val target = system.actorOf(Runtime.props(Env()))

      probe.send(target, Runtime.Commands.RemoveProcess(0))
      probe.expectMsg(Runtime.Responses.ProcessNotRegistered)
    }

    "Inject process reference by Inject message" in {
      val probe = TestProbe()
      val target = system.actorOf(Runtime.props(Env()))
      val process = TestProbe()

      probe.send(target, Runtime.Commands.ReservePid)
      val pid = probe.expectMsgType[Runtime.Responses.Pid]
      probe.send(target, Runtime.Commands.Inject(process.ref, pid.id))
      probe.expectMsg(Runtime.Responses.Injected)
      probe.send(target, Runtime.Commands.GetProcess(pid.id))
      probe.expectMsg(Runtime.Responses.Process(Some(process.ref)))
    }

    "Return PidNotReserved for trying Inject precess reference to non-existed pid" in {
      val probe = TestProbe()
      val process = TestProbe()
      val target = system.actorOf(Runtime.props(Env()))

      probe.send(target, Runtime.Commands.Inject(process.ref, 0))
      probe.expectMsg(Runtime.Responses.PidNotReserved)
    }

    "For Spawn message create and exec SpawnFsm" in {
      val probe = TestProbe()
      val initiator = TestProbe()
      val target = system.actorOf(Runtime.props(Env()))

      probe.send(target, Runtime.Commands.Spawn("echo", Vector("a"), SystemCommandDefinition(""), initiator.ref))
      probe.expectMsgType[ProcessConstructor.Responses.ProcessDef]
    }
  }



}
