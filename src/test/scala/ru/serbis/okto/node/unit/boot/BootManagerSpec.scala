package ru.serbis.okto.node.unit.boot

import java.io.File

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import akka.util.ByteString
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import ru.serbis.okto.node.boot.BootManager
import ru.serbis.okto.node.common.{CommandsUnion, Env}
import ru.serbis.okto.node.log.{StdOutLogger, StreamLogger}
import ru.serbis.okto.node.proxy.system.TestActorSystemProxy
import ru.serbis.okto.node.runtime.Stream
import ru.serbis.okto.node.reps.{BootRep, ScriptsRep}
import ru.serbis.okto.node.reps.SyscomsRep.Responses.SystemCommandDefinition
import ru.serbis.okto.node.reps.UsercomsRep.Responses.UserCommandDefinition
import ru.serbis.okto.node.runtime.{ProcessConstructor, Runtime, SpawnFsm}
import ru.serbis.okto.node.syscoms.shell.Shell
import ru.serbis.okto.node.common.ReachTypes.ReachByteString

import scala.concurrent.duration._

class BootManagerSpec extends TestKit(ActorSystem("TestSystem")) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll with StreamLogger {

  override protected def beforeAll(): Unit = {
    initializeGlobalLogger(system)
    logger.addDestination(system.actorOf(StdOutLogger.props))
  }

  "BooManager" must {
    "Process positive test" in {
      val runtime = TestProbe()
      val bootRep = TestProbe()
      val systemProbe = TestProbe()
      val shell1 = TestProbe()
      val shell2 = TestProbe()
      val shell3 = TestProbe()
      val env = Env(bootRep = bootRep.ref, runtime = runtime.ref)


      val target = system.actorOf(BootManager.props(env, new TestActorSystemProxy(systemProbe.ref, system)))
      target ! BootManager.Commands.Exec()
      bootRep.expectMsg(BootRep.Commands.GetAll)
      bootRep.reply(List(
        BootRep.Responses.BootDefinition(1, "cmd 1"),
        BootRep.Responses.BootDefinition(2, "cmd 2"),
        BootRep.Responses.BootDefinition(3, "cmd 3")
      ))

      //---

      var props = systemProbe.expectMsgType[TestActorSystemProxy.Actions.ActorOf]
      props.props.actorClass() + "$" shouldEqual Shell.getClass.toString
      systemProbe.reply(shell1.ref)
      shell1.expectMsg(CommandsUnion.Commands.Run(system.deadLetters, Map(0 -> target, 1 -> target)))
      shell1.send(target, Stream.Commands.WriteWrapped(ByteString().prompt))
      shell1.expectMsg(Stream.Responses.Data(ByteString("cmd 1").eoi))

      //---

      props = systemProbe.expectMsgType[TestActorSystemProxy.Actions.ActorOf]
      props.props.actorClass() + "$" shouldEqual Shell.getClass.toString
      systemProbe.reply(shell2.ref)
      shell2.expectMsg(CommandsUnion.Commands.Run(system.deadLetters, Map(0 -> target, 1 -> target)))
      shell2.send(target, Stream.Commands.WriteWrapped(ByteString().prompt))
      shell2.expectMsg(Stream.Responses.Data(ByteString("cmd 2").eoi))

      //---

      props = systemProbe.expectMsgType[TestActorSystemProxy.Actions.ActorOf]
      props.props.actorClass() + "$" shouldEqual Shell.getClass.toString
      systemProbe.reply(shell3.ref)
      shell3.expectMsg(CommandsUnion.Commands.Run(system.deadLetters, Map(0 -> target, 1 -> target)))
      shell3.send(target, Stream.Commands.WriteWrapped(ByteString().prompt))
      shell3.expectMsg(Stream.Responses.Data(ByteString("cmd 3").eoi))
    }
  }
}
