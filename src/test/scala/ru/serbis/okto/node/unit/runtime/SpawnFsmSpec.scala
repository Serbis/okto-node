package ru.serbis.okto.node.unit.runtime

import java.io.File

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import ru.serbis.okto.node.common.Env
import ru.serbis.okto.node.log.{StdOutLogger, StreamLogger}
import ru.serbis.okto.node.reps.ScriptsRep
import ru.serbis.okto.node.reps.SyscomsRep.Responses.SystemCommandDefinition
import ru.serbis.okto.node.reps.UsercomsRep.Responses.UserCommandDefinition
import ru.serbis.okto.node.runtime.{ProcessConstructor, Runtime, SpawnFsm}

import scala.concurrent.duration._

class SpawnFsmSpec extends TestKit(ActorSystem("TestSystem")) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll with StreamLogger {

  val commandsDat = new File("fect/commands.dat")

  override protected def beforeAll(): Unit = {
    initializeGlobalLogger(system)
    logger.addDestination(system.actorOf(StdOutLogger.props))
  }

  "SpawnFsm" must {
    "Pass through the procedure for creating a process using the system command script" in {
      val probe = TestProbe()
      val initiator = TestProbe()
      val runtime = TestProbe()
      val systemCommandsRep = TestProbe()
      val userCommandsRep = TestProbe()
      val env = Env(syscomsRep = systemCommandsRep.ref, usercomsRep = userCommandsRep.ref, runtime = runtime.ref)

      val target = system.actorOf(SpawnFsm.props(env))
      probe.send(target, SpawnFsm.Commands.Exec("shell", Vector("a"), SystemCommandDefinition(""), initiator.ref, "shell"))
      runtime.expectMsg(Runtime.Commands.ReservePid)
      runtime.reply(Runtime.Responses.Pid(1000))
      val inject = runtime.expectMsgType[Runtime.Commands.Inject]
      inject.procDef.pid shouldEqual 1000
      runtime.reply(Runtime.Responses.Injected)
      val procDef = probe.expectMsgType[ProcessConstructor.Responses.ProcessDef]
      procDef.pid shouldEqual 1000
    }

    "For creating a process using the user command script" should {
      "Create new not started process" in {
        val probe = TestProbe()
        val initiator = TestProbe()
        val runtime = TestProbe()
        val systemCommandsRep = TestProbe()
        val userCommandsRep = TestProbe()
        val scriptRep = TestProbe()
        val env = Env(syscomsRep = systemCommandsRep.ref, usercomsRep = userCommandsRep.ref, runtime = runtime.ref, scriptsRep = scriptRep.ref)

        val target = system.actorOf(SpawnFsm.props(env))
        probe.send(target, SpawnFsm.Commands.Exec("userEcho", Vector("a"), UserCommandDefinition("userEcho", "userEcho.js"), initiator.ref, "shell"))
        scriptRep.expectMsg(ScriptsRep.Commands.GetScript("userEcho.js"))
        scriptRep.reply(ScriptsRep.Responses.Script("xxx"))

        runtime.expectMsg(Runtime.Commands.ReservePid)
        runtime.reply(Runtime.Responses.Pid(1001))
        val inject = runtime.expectMsgType[Runtime.Commands.Inject]
        inject.procDef.pid shouldEqual 1001
        runtime.reply(Runtime.Responses.Injected)
        val procDef = probe.expectMsgType[ProcessConstructor.Responses.ProcessDef]
        procDef.pid shouldEqual 1001
      }

      "Return SpawnError if target script file does not exists on the disk" in {
        val probe = TestProbe()
        val initiator = TestProbe()
        val runtime = TestProbe()
        val systemCommandsRep = TestProbe()
        val userCommandsRep = TestProbe()
        val scriptRep = TestProbe()
        val env = Env(syscomsRep = systemCommandsRep.ref, usercomsRep = userCommandsRep.ref, runtime = runtime.ref, scriptsRep = scriptRep.ref)

        val target = system.actorOf(SpawnFsm.props(env))
        probe.send(target, SpawnFsm.Commands.Exec("userEcho", Vector("a"), UserCommandDefinition("userEcho", "userEcho.js"), initiator.ref, "shell"))
        scriptRep.expectMsg(ScriptsRep.Commands.GetScript("userEcho.js"))
        scriptRep.reply(ScriptsRep.Responses.ScriptNotFound)
        probe.expectMsg(Runtime.Responses.SpawnError)
      }

      "Return SpawnError response from scriptRep timeout was reached" in {
        val probe = TestProbe()
        val initiator = TestProbe()
        val runtime = TestProbe()
        val systemCommandsRep = TestProbe()
        val userCommandsRep = TestProbe()
        val scriptRep = TestProbe()
        val env = Env(syscomsRep = systemCommandsRep.ref, usercomsRep = userCommandsRep.ref, runtime = runtime.ref, scriptsRep = scriptRep.ref)

        val target = system.actorOf(SpawnFsm.props(env, testMode = true))
        probe.send(target, SpawnFsm.Commands.Exec("userEcho", Vector("a"), UserCommandDefinition("userEcho", "userEcho.js"), initiator.ref, "shell"))
        scriptRep.expectMsg(ScriptsRep.Commands.GetScript("userEcho.js"))
        probe.expectMsg(Runtime.Responses.SpawnError)
      }
    }

    "Return SpawnError if with Exec command was passed non-exist command" in {
      val probe = TestProbe()
      val initiator = TestProbe()
      val runtime = TestProbe()
      val systemCommandsRep = TestProbe()
      val userCommandsRep = TestProbe()
      val env = Env(syscomsRep = systemCommandsRep.ref, usercomsRep = userCommandsRep.ref, runtime = runtime.ref)

      val target = system.actorOf(SpawnFsm.props(env))
      probe.send(target, SpawnFsm.Commands.Exec("xxx", Vector("a"), SystemCommandDefinition(""), initiator.ref, "shell"))
      probe.expectMsg(Runtime.Responses.SpawnError)
    }

    "Return SpawnError if with ProcessConstructor respond with Error" in {
      val probe = TestProbe()
      val initiator = TestProbe()
      val runtime = TestProbe()
      val systemCommandsRep = TestProbe()
      val userCommandsRep = TestProbe()
      val env = Env(syscomsRep = systemCommandsRep.ref, usercomsRep = userCommandsRep.ref, runtime = runtime.ref)

      val target = system.actorOf(SpawnFsm.props(env))
      probe.send(target, SpawnFsm.Commands.Exec("shell", Vector("a"), SystemCommandDefinition(""), initiator.ref, "shell"))
      runtime.expectMsg(Runtime.Commands.ReservePid)
      probe.expectMsg(10 second, Runtime.Responses.SpawnError)
    }

    "Return SpawnError if with Runtime does not respond with expected timeout" in {
      val probe = TestProbe()
      val initiator = TestProbe()
      val runtime = TestProbe()
      val systemCommandsRep = TestProbe()
      val userCommandsRep = TestProbe()
      val env = Env(syscomsRep = systemCommandsRep.ref, usercomsRep = userCommandsRep.ref, runtime = runtime.ref)

      val target = system.actorOf(SpawnFsm.props(env, testMode = true))
      probe.send(target, SpawnFsm.Commands.Exec("shell", Vector("a"), SystemCommandDefinition(""), initiator.ref, "shell"))
      runtime.expectMsg(Runtime.Commands.ReservePid)
      runtime.reply(Runtime.Responses.Pid(1003))
      val inject = runtime.expectMsgType[Runtime.Commands.Inject]
      inject.procDef.pid shouldEqual 1003
      probe.expectMsg(Runtime.Responses.SpawnError)
    }
  }
}
