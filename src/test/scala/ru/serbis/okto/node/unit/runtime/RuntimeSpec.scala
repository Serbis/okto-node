package ru.serbis.okto.node.unit.runtime

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import ru.serbis.okto.node.common.Env
import ru.serbis.okto.node.log.{StdOutLogger, StreamLogger}
import ru.serbis.okto.node.reps.SyscomsRep.Responses.SystemCommandDefinition
import ru.serbis.okto.node.runtime.ProcessConstructor.Responses.ProcessDef
import ru.serbis.okto.node.runtime.{CmdExecutor, ProcessConstructor, Runtime}

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
      probe.send(target, Runtime.Commands.Inject(ProcessDef(process.ref, null, pid.id, Map.empty, 0, "", "", ("", 0))))
      probe.expectMsg(Runtime.Responses.Injected)
      probe.send(target, Runtime.Commands.GetProcess(pid.id))
      probe.expectMsg(Runtime.Responses.Process(Some(process.ref)))
    }

    "Return PidNotReserved for trying Inject precess reference to non-existed pid" in {
      val probe = TestProbe()
      val process = TestProbe()
      val target = system.actorOf(Runtime.props(Env()))

      probe.send(target, Runtime.Commands.Inject(ProcessDef(process.ref, null, 0, Map.empty, 0, "", "", ("", 0))))
      probe.expectMsg(Runtime.Responses.PidNotReserved)
    }

    "For Spawn message create and exec SpawnFsm" in {
      val probe = TestProbe()
      val initiator = TestProbe()
      val target = system.actorOf(Runtime.props(Env()))

      probe.send(target, Runtime.Commands.Spawn("echo", Vector("a"), SystemCommandDefinition(""), initiator.ref, "shell"))
      probe.expectMsgType[ProcessConstructor.Responses.ProcessDef]
    }

    "For GetProcessesDefs must return list of the all run processes for None pids list" in {
      val probe = TestProbe()
      val target = system.actorOf(Runtime.props(Env()))
      val process = TestProbe()

      probe.send(target, Runtime.Commands.ReservePid)
      val pid0 = probe.expectMsgType[Runtime.Responses.Pid]
      probe.send(target, Runtime.Commands.ReservePid)
      val pid1 = probe.expectMsgType[Runtime.Responses.Pid]
      probe.send(target, Runtime.Commands.ReservePid)
      val pid2 = probe.expectMsgType[Runtime.Responses.Pid]
      val pdList = List(
        ProcessDef(process.ref, null, pid0.id, Map.empty, 0, "", "", ("", 0)),
        ProcessDef(process.ref, null, pid1.id, Map.empty, 0, "", "", ("", 0)),
        ProcessDef(process.ref, null, pid2.id, Map.empty, 0, "", "", ("", 0))
      )

      probe.send(target, Runtime.Commands.Inject(pdList.head))
      probe.expectMsg(Runtime.Responses.Injected)
      probe.send(target, Runtime.Commands.Inject(pdList(1)))
      probe.expectMsg(Runtime.Responses.Injected)
      probe.send(target, Runtime.Commands.Inject(pdList(2)))
      probe.expectMsg(Runtime.Responses.Injected)
      probe.send(target, Runtime.Commands.GetProcessesDefs(None))
      val r = probe.expectMsgType[Runtime.Responses.ProcessesDefs]
      pdList should contain (r.defs.head)
      pdList should contain (r.defs(1))
      pdList should contain (r.defs(2))
    }

    "For GetProcessesDefs must return list of the processes with pids specified in the command" in {
      val probe = TestProbe()
      val target = system.actorOf(Runtime.props(Env()))
      val process = TestProbe()

      probe.send(target, Runtime.Commands.ReservePid)
      val pid0 = probe.expectMsgType[Runtime.Responses.Pid]
      probe.send(target, Runtime.Commands.ReservePid)
      val pid1 = probe.expectMsgType[Runtime.Responses.Pid]
      probe.send(target, Runtime.Commands.ReservePid)
      val pid2 = probe.expectMsgType[Runtime.Responses.Pid]
      val pdList = List(
        ProcessDef(process.ref, null, pid0.id, Map.empty, 0, "", "", ("", 0)),
        ProcessDef(process.ref, null, pid1.id, Map.empty, 0, "", "", ("", 0)),
        ProcessDef(process.ref, null, pid2.id, Map.empty, 0, "", "", ("", 0))
      )

      probe.send(target, Runtime.Commands.Inject(pdList.head))
      probe.expectMsg(Runtime.Responses.Injected)
      probe.send(target, Runtime.Commands.Inject(pdList(1)))
      probe.expectMsg(Runtime.Responses.Injected)
      probe.send(target, Runtime.Commands.Inject(pdList(2)))
      probe.expectMsg(Runtime.Responses.Injected)
      probe.send(target, Runtime.Commands.GetProcessesDefs(Some(List(pid0.id, pid2.id))))
      val r = probe.expectMsgType[Runtime.Responses.ProcessesDefs]
      r.defs.size shouldEqual 2
      pdList should contain (r.defs.head)
      pdList should contain (r.defs(1))
    }

    "For SendSignal message must send Signal to the process executor" in {
      val probe = TestProbe()
      val target = system.actorOf(Runtime.props(Env()))
      val procStub = TestProbe()
      val executor0 = TestProbe()
      val executor1 = TestProbe()
      val executor2 = TestProbe()

      probe.send(target, Runtime.Commands.ReservePid)
      val pid0 = probe.expectMsgType[Runtime.Responses.Pid]
      probe.send(target, Runtime.Commands.ReservePid)
      val pid1 = probe.expectMsgType[Runtime.Responses.Pid]
      probe.send(target, Runtime.Commands.ReservePid)
      val pid2 = probe.expectMsgType[Runtime.Responses.Pid]
      val pdList = List(
        ProcessDef(procStub.ref, executor0.ref, pid0.id, Map.empty, 0, "", "", ("", 0)),
        ProcessDef(procStub.ref, executor1.ref, pid1.id, Map.empty, 0, "", "", ("", 0)),
        ProcessDef(procStub.ref, executor2.ref, pid2.id, Map.empty, 0, "", "", ("", 0))
      )

      probe.send(target, Runtime.Commands.Inject(pdList.head))
      probe.expectMsg(Runtime.Responses.Injected)
      probe.send(target, Runtime.Commands.Inject(pdList(1)))
      probe.expectMsg(Runtime.Responses.Injected)
      probe.send(target, Runtime.Commands.Inject(pdList(2)))
      probe.expectMsg(Runtime.Responses.Injected)

      probe.send(target, Runtime.Commands.SendSignal(pid1.id, 9))
      executor1.expectMsg(CmdExecutor.ControlMessages.Signal(9))
      probe.expectMsg(Runtime.Responses.SignalSent)
    }

    "For SendSignal message must return ProcessNotRegistered if process does not exist" in {
      val probe = TestProbe()
      val target = system.actorOf(Runtime.props(Env()))
      val procStub = TestProbe()
      val executor0 = TestProbe()
      val executor1 = TestProbe()
      val executor2 = TestProbe()

      probe.send(target, Runtime.Commands.ReservePid)
      val pid0 = probe.expectMsgType[Runtime.Responses.Pid]
      probe.send(target, Runtime.Commands.ReservePid)
      val pid1 = probe.expectMsgType[Runtime.Responses.Pid]
      probe.send(target, Runtime.Commands.ReservePid)
      val pid2 = probe.expectMsgType[Runtime.Responses.Pid]
      val pdList = List(
        ProcessDef(procStub.ref, executor0.ref, pid0.id, Map.empty, 0, "", "", ("", 0)),
        ProcessDef(procStub.ref, executor1.ref, pid1.id, Map.empty, 0, "", "", ("", 0)),
        ProcessDef(procStub.ref, executor2.ref, pid2.id, Map.empty, 0, "", "", ("", 0))
      )

      probe.send(target, Runtime.Commands.Inject(pdList.head))
      probe.expectMsg(Runtime.Responses.Injected)
      probe.send(target, Runtime.Commands.Inject(pdList(1)))
      probe.expectMsg(Runtime.Responses.Injected)
      probe.send(target, Runtime.Commands.Inject(pdList(2)))
      probe.expectMsg(Runtime.Responses.Injected)

      probe.send(target, Runtime.Commands.SendSignal(1000, 9))
      probe.expectMsg(Runtime.Responses.ProcessNotRegistered)
    }
  }



}
