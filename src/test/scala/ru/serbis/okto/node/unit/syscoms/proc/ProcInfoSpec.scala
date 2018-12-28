package ru.serbis.okto.node.unit.syscoms.proc

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import ru.serbis.okto.node.common.Env
import ru.serbis.okto.node.log.{StdOutLogger, StreamLogger}
import ru.serbis.okto.node.reps.StorageRep
import ru.serbis.okto.node.reps.StorageRep.Responses.FileInfo
import ru.serbis.okto.node.runtime.ProcessConstructor.Responses.ProcessDef
import ru.serbis.okto.node.syscoms.proc.{Proc, ProcInfo}
import ru.serbis.okto.node.runtime.Runtime

class ProcInfoSpec extends TestKit(ActorSystem("TestSystem")) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll with StreamLogger {


  override protected def beforeAll(): Unit = {
    initializeGlobalLogger(system)
    logger.addDestination(system.actorOf(StdOutLogger.props))
  }


  "ProcInfo" must {
    "Process positive test -> no sub options" in {
      val probe = TestProbe()
      val runtime = TestProbe()
      val stdOut = TestProbe()
      val stdIn = TestProbe()
      val env = Env(runtime = runtime.ref)
      val target = system.actorOf(ProcInfo.props(Vector.empty, env, stdIn.ref, stdOut.ref))

      probe.send(target, ProcInfo.Commands.Exec)
      runtime.expectMsg(Runtime.Commands.GetProcessesDefs(None))
      runtime.reply(Runtime.Responses.ProcessesDefs(List(
        ProcessDef(null, null, 0, Map.empty, 10, "p0", "shell", ("root", 0)),
        ProcessDef(null, null, 1, Map.empty, 20, "p1", "shell", ("root", 0)),
        ProcessDef(null, null, 2, Map.empty, 30, "p2", "shell", ("root", 0))
      )))
      probe.expectMsg(Proc.Internals.Complete(0, "[\n\t{\n\t\t\"pid\" : 0,\n\t\t\"createTime\" : 10,\n\t\t\"command\" : \"p0\",\n\t\t\"initiator\" : \"shell\",\n\t\t\"owner\" : [\"root\", 0]\n\t},\n\t{\n\t\t\"pid\" : 1,\n\t\t\"createTime\" : 20,\n\t\t\"command\" : \"p1\",\n\t\t\"initiator\" : \"shell\",\n\t\t\"owner\" : [\"root\", 0]\n\t},\n\t{\n\t\t\"pid\" : 2,\n\t\t\"createTime\" : 30,\n\t\t\"command\" : \"p2\",\n\t\t\"initiator\" : \"shell\",\n\t\t\"owner\" : [\"root\", 0]\n\t}\n]"))
    }

    "Process positive test -> -n" in {
      val probe = TestProbe()
      val runtime = TestProbe()
      val stdOut = TestProbe()
      val stdIn = TestProbe()
      val env = Env(runtime = runtime.ref)
      val target = system.actorOf(ProcInfo.props(Vector("-p", "1"), env, stdIn.ref, stdOut.ref))

      probe.send(target, ProcInfo.Commands.Exec)
      runtime.expectMsg(Runtime.Commands.GetProcessesDefs(Some(List(1))))
      runtime.reply(Runtime.Responses.ProcessesDefs(List(
        ProcessDef(null, null, 1, Map.empty, 20, "p1", "shell", ("root", 0))
      )))
      probe.expectMsg(Proc.Internals.Complete(0, "[\n\t{\n\t\t\"pid\" : 1,\n\t\t\"createTime\" : 20,\n\t\t\"command\" : \"p1\",\n\t\t\"initiator\" : \"shell\",\n\t\t\"owner\" : [\"root\", 0]\n\t}\n]"))
    }

    "Complete with error code if -p option is not a number" in {
      val probe = TestProbe()
      val runtime = TestProbe()
      val stdOut = TestProbe()
      val stdIn = TestProbe()
      val env = Env(runtime = runtime.ref)
      val target = system.actorOf(ProcInfo.props(Vector("-p", "x"), env, stdIn.ref, stdOut.ref))

      probe.send(target, ProcInfo.Commands.Exec)
      probe.expectMsg(Proc.Internals.Complete(10, "-p option is not a number"))
    }

    "Return error if runtime does not respond with expected timeout" in {
      val probe = TestProbe()
      val runtime = TestProbe()
      val stdOut = TestProbe()
      val stdIn = TestProbe()
      val env = Env(runtime = runtime.ref)
      val target = system.actorOf(ProcInfo.props(Vector.empty, env, stdIn.ref, stdOut.ref, testMode = true))
      probe.send(target, ProcInfo.Commands.Exec)
      probe.expectMsg(Proc.Internals.Complete(11, "Runtime timeout"))
    }
  }
}