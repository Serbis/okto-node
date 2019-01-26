package ru.serbis.okto.node.unit.syscoms.shell

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import akka.util.ByteString
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import ru.serbis.okto.node.common.{CommandsUnion, Env}
import ru.serbis.okto.node.log.{StdOutLogger, StreamLogger}
import ru.serbis.okto.node.reps.SyscomsRep.Responses.SystemCommandDefinition
import ru.serbis.okto.node.reps.UsercomsRep.Responses.UserCommandDefinition
import ru.serbis.okto.node.reps.{SyscomsRep, UsercomsRep}
import ru.serbis.okto.node.runtime.ProcessConstructor.Responses.ProcessDef
import ru.serbis.okto.node.runtime.{ProcessConstructor, Runtime, Stream}
import ru.serbis.okto.node.runtime.StreamControls._
import ru.serbis.okto.node.syscoms.shell.StatementsParser.CommandNode
import ru.serbis.okto.node.syscoms.shell.{PipePreparator, Shell}

class PipePreparatorSpec extends TestKit(ActorSystem("TestSystem")) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll with StreamLogger {


  override protected def beforeAll(): Unit = {
    initializeGlobalLogger(system)
    logger.addDestination(system.actorOf(StdOutLogger.props))
  }

  "PipePreparator" must {
    "Pass the positive test" in {
      val probe = TestProbe()
      val usercomsRep = TestProbe()
      val syscomsRep = TestProbe()
      val runtime = TestProbe()
      val process1 = TestProbe()
      val executor = TestProbe()
      val stdIn1 = TestProbe()
      val stdOut1 = TestProbe()
      val env = Env(usercomsRep = usercomsRep.ref, syscomsRep = syscomsRep.ref, runtime = runtime.ref)

      val target = system.actorOf(PipePreparator.props(env, "shell"))
      probe.send(target, PipePreparator.Commands.Exec(List(CommandNode("a", Vector.empty), CommandNode("b", Vector.empty), CommandNode("c", Vector.empty))))
      syscomsRep.expectMsg(SyscomsRep.Commands.GetCommandsBatch(List("a", "b", "c")))
      syscomsRep.reply(SyscomsRep.Responses.CommandsBatch(Map(
        "a" -> Some(SystemCommandDefinition("a.class")),
        "b" -> Some(SystemCommandDefinition("b.class")),
        "c" -> None
      )))
      usercomsRep.expectMsg(UsercomsRep.Commands.GetCommandsBatch(List("a", "b", "c")))
      usercomsRep.reply(UsercomsRep.Responses.CommandsBatch(Map(
        "a" -> None,
        "b" -> None,
        "c" -> Some(UserCommandDefinition("c", "c.class"))
      )))

      // этот кусок кода являет заглушкой до разработки полноценного пайпинга. Сейчас PipePreparator возвращает замыкние потоков по первой команде из списка

      runtime.expectMsg(Runtime.Commands.Spawn("a", Vector.empty, SystemCommandDefinition("a.class"), target, "shell"))
      runtime.reply(ProcessDef(process1.ref, executor.ref, 1000, Map(0 -> stdOut1.ref, 1 -> stdIn1.ref), 0, "", "", ("root", 0)))
      probe.expectMsg(PipePreparator.Responses.PipeCircuit(stdIn1.ref, stdOut1.ref))

      //REVERSE TEST

      val target2 = system.actorOf(PipePreparator.props(env, "shell"))
      probe.send(target2, PipePreparator.Commands.Exec(List(CommandNode("a", Vector.empty), CommandNode("b", Vector.empty), CommandNode("c", Vector.empty))))
      usercomsRep.expectMsg(UsercomsRep.Commands.GetCommandsBatch(List("a", "b", "c")))
      usercomsRep.reply(UsercomsRep.Responses.CommandsBatch(Map(
        "a" -> None,
        "b" -> None,
        "c" -> Some(UserCommandDefinition("c", "c.class"))
      )))
      syscomsRep.expectMsg(SyscomsRep.Commands.GetCommandsBatch(List("a", "b", "c")))
      syscomsRep.reply(SyscomsRep.Responses.CommandsBatch(Map(
        "a" -> Some(SystemCommandDefinition("a.class")),
        "b" -> Some(SystemCommandDefinition("b.class")),
        "c" -> None
      )))

      runtime.expectMsg(Runtime.Commands.Spawn("a", Vector.empty, SystemCommandDefinition("a.class"), target2, "shell"))
      runtime.reply(ProcessDef(process1.ref, executor.ref, 1000, Map(0 -> stdOut1.ref, 1 -> stdIn1.ref), 0, "", "", ("root", 0)))
      probe.expectMsg(PipePreparator.Responses.PipeCircuit(stdIn1.ref, stdOut1.ref))
    }

    "Return InternalError if CollectRepsResponses timeout was reached" in {
      val probe = TestProbe()
      val usercomsRep = TestProbe()
      val syscomsRep = TestProbe()
      val runtime = TestProbe()
      val env = Env(usercomsRep = usercomsRep.ref, syscomsRep = syscomsRep.ref, runtime = runtime.ref)

      val target = system.actorOf(PipePreparator.props(env, "shell", testMode = true))
      probe.send(target, PipePreparator.Commands.Exec(List(CommandNode("a", Vector.empty), CommandNode("b", Vector.empty), CommandNode("c", Vector.empty))))
      probe.expectMsg(PipePreparator.Responses.InternalError)
    }

    "Return InternalError if ProcessesSpawning timeout was reached" in {
      val probe = TestProbe()
      val usercomsRep = TestProbe()
      val syscomsRep = TestProbe()
      val runtime = TestProbe()
      val env = Env(usercomsRep = usercomsRep.ref, syscomsRep = syscomsRep.ref, runtime = runtime.ref)

      val target = system.actorOf(PipePreparator.props(env, "shell", testMode = true))
      probe.send(target, PipePreparator.Commands.Exec(List(CommandNode("a", Vector.empty), CommandNode("b", Vector.empty), CommandNode("c", Vector.empty))))
      syscomsRep.expectMsg(SyscomsRep.Commands.GetCommandsBatch(List("a", "b", "c")))
      syscomsRep.reply(SyscomsRep.Responses.CommandsBatch(Map(
        "a" -> Some(SystemCommandDefinition("a.class")),
        "b" -> Some(SystemCommandDefinition("b.class")),
        "c" -> None
      )))
      usercomsRep.expectMsg(UsercomsRep.Commands.GetCommandsBatch(List("a", "b", "c")))
      usercomsRep.reply(UsercomsRep.Responses.CommandsBatch(Map(
        "a" -> None,
        "b" -> None,
        "c" -> Some(UserCommandDefinition("c", "c.class"))
      )))

      runtime.expectMsg(Runtime.Commands.Spawn("a", Vector.empty, SystemCommandDefinition("a.class"), target, "shell"))
      probe.expectMsg(PipePreparator.Responses.InternalError)
    }

    "Return CommandsNotFound if some of the passed commands does not exist" in {
      val probe = TestProbe()
      val usercomsRep = TestProbe()
      val syscomsRep = TestProbe()
      val runtime = TestProbe()
      val env = Env(usercomsRep = usercomsRep.ref, syscomsRep = syscomsRep.ref, runtime = runtime.ref)

      val target = system.actorOf(PipePreparator.props(env, "shell"))
      probe.send(target, PipePreparator.Commands.Exec(List(CommandNode("a", Vector.empty), CommandNode("b", Vector.empty), CommandNode("c", Vector.empty))))
      syscomsRep.expectMsg(SyscomsRep.Commands.GetCommandsBatch(List("a", "b", "c")))
      syscomsRep.reply(SyscomsRep.Responses.CommandsBatch(Map(
        "a" -> None,
        "b" -> None,
        "c" -> None
      )))
      usercomsRep.expectMsg(UsercomsRep.Commands.GetCommandsBatch(List("a", "b", "c")))
      usercomsRep.reply(UsercomsRep.Responses.CommandsBatch(Map(
        "a" -> None,
        "b" -> None,
        "c" -> Some(UserCommandDefinition("c", "c.class"))
      )))
      probe.expectMsg(PipePreparator.Responses.CommandsNotFound(Set("a", "b")))
    }

    "Return InternalError if some process spawning procedure was returned Error" in { // (also it must destroy all before created process)
      val probe = TestProbe()
      val usercomsRep = TestProbe()
      val syscomsRep = TestProbe()
      val runtime = TestProbe()
      val env = Env(usercomsRep = usercomsRep.ref, syscomsRep = syscomsRep.ref, runtime = runtime.ref)

      val target = system.actorOf(PipePreparator.props(env, "shell"))
      probe.send(target, PipePreparator.Commands.Exec(List(CommandNode("a", Vector.empty), CommandNode("b", Vector.empty), CommandNode("c", Vector.empty))))
      syscomsRep.expectMsg(SyscomsRep.Commands.GetCommandsBatch(List("a", "b", "c")))
      syscomsRep.reply(SyscomsRep.Responses.CommandsBatch(Map(
        "a" -> Some(SystemCommandDefinition("a.class")),
        "b" -> Some(SystemCommandDefinition("b.class")),
        "c" -> None
      )))
      usercomsRep.expectMsg(UsercomsRep.Commands.GetCommandsBatch(List("a", "b", "c")))
      usercomsRep.reply(UsercomsRep.Responses.CommandsBatch(Map(
        "a" -> None,
        "b" -> None,
        "c" -> Some(UserCommandDefinition("c", "c.class"))
      )))

      runtime.expectMsg(Runtime.Commands.Spawn("a", Vector.empty, SystemCommandDefinition("a.class"), target, "shell"))
      runtime.reply(Runtime.Responses.SpawnError)
      probe.expectMsg(PipePreparator.Responses.InternalError)
    }
  }
}
