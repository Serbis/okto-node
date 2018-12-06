package ru.serbis.okto.node.unit.runtime

import java.io.File

import akka.actor.{Actor, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import ru.serbis.okto.node.common.Env
import ru.serbis.okto.node.log.{StdOutLogger, StreamLogger}
import ru.serbis.okto.node.runtime.{Process, ProcessConstructor, Runtime, Stream}


class ProcessConstructorSpec extends TestKit(ActorSystem("TestSystem")) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll with StreamLogger {

  val commandsDat = new File("fect/commands.dat")

  override protected def beforeAll(): Unit = {
    initializeGlobalLogger(system)
    logger.addDestination(system.actorOf(StdOutLogger.props))
  }

  class FictionExecutor extends Actor {
    override def receive = {
      case "xxx" => sender() ! "yyy"
    }
  }

  "ProcessConstructor" must {
    "After complete work return fully initialized process in not started state" in {
      val probe = TestProbe()
      val stdOutReceiver = TestProbe()
      val runtime = TestProbe()

      val env = Env(runtime = runtime.ref)

      val target = system.actorOf(ProcessConstructor.props(env))
      probe.send(target, ProcessConstructor.Commands.Exec(system.actorOf(Props(new FictionExecutor)), stdOutReceiver.ref))
      runtime.expectMsg(Runtime.Commands.ReservePid)
      runtime.reply(Runtime.Responses.Pid(1000))
      val process = probe.expectMsgType[ProcessConstructor.Responses.ProcessDef]
      process.pid shouldEqual 1000
      probe.send(process.ref, Process.Commands.GetPid)
      probe.expectMsg(Process.Responses.Pid(1000))
      probe.send(process.ref, Process.Commands.GetExecutor)
      val executor = probe.expectMsgType[Process.Responses.Executor].ref
      probe.send(executor, "xxx")
      probe.expectMsg("yyy")
      probe.send(process.ref, Process.Commands.GetStreams)
      val streams = probe.expectMsgType[Process.Responses.Streams].value
      streams shouldEqual process.streams
      probe.send(streams(0), Stream.Commands.GetPid)
      probe.expectMsg(Stream.Responses.Pid(1000))
      probe.send(streams(1), Stream.Commands.GetPid)
      probe.expectMsg(Stream.Responses.Pid(1000))
    }
  }
}

