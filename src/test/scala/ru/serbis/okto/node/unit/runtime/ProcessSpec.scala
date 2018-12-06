package ru.serbis.okto.node.unit.runtime

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import akka.util.ByteString
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import ru.serbis.okto.node.common.{CommandsUnion, Env}
import ru.serbis.okto.node.log.{StdOutLogger, StreamLogger}
import ru.serbis.okto.node.runtime.Stream
import ru.serbis.okto.node.runtime.Process
import ru.serbis.okto.node.runtime.Runtime
import ru.serbis.okto.node.runtime.Process.Responses.Executor
import ru.serbis.okto.node.proto.{messages => proto_messages}

import scala.concurrent.duration._

class ProcessSpec extends TestKit(ActorSystem("TestSystem")) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll with StreamLogger {

  override protected def beforeAll(): Unit = {
    initializeGlobalLogger(system)
    logger.addDestination(system.actorOf(StdOutLogger.props))
  }

  "Process" must {
    "Return pid by GetPid request" in {
      val probe = TestProbe()
      val executor = TestProbe()
      val initiator = TestProbe()
      val runtime = TestProbe()
      val stream0 = TestProbe()
      val stream1 = TestProbe()
      val streams = Map (
        0 -> stream0.ref,
        1 -> stream1.ref
      )
      val env = Env(runtime = runtime.ref)

      val target = system.actorOf(Process.props(env, initiator.ref, executor.ref, streams, 1000))
      probe.send(target, Process.Commands.GetPid)
      probe.expectMsg(Process.Responses.Pid(1000))
    }

    "Return executor by GetExecutor request" in {
      val probe = TestProbe()
      val executor = TestProbe()
      val initiator = TestProbe()
      val runtime = TestProbe()
      val stream0 = TestProbe()
      val stream1 = TestProbe()
      val streams = Map (
        0 -> stream0.ref,
        1 -> stream1.ref
      )
      val env = Env(runtime = runtime.ref)

      val target = system.actorOf(Process.props(env, initiator.ref, executor.ref, streams, 1000))
      probe.send(target, Process.Commands.GetExecutor)
      probe.expectMsg(Process.Responses.Executor(executor.ref))
    }

    "Return streams map by GetStreams request" in {
      val probe = TestProbe()
      val executor = TestProbe()
      val initiator = TestProbe()
      val runtime = TestProbe()
      val stream0 = TestProbe()
      val stream1 = TestProbe()
      val streams = Map (
        0 -> stream0.ref,
        1 -> stream1.ref
      )
      val env = Env(runtime = runtime.ref)

      val target = system.actorOf(Process.props(env, initiator.ref, executor.ref, streams, 1000))
      probe.send(target, Process.Commands.GetStreams)
      probe.expectMsg(Process.Responses.Streams(streams))
    }

    "Run executor by Start message" in {
      val probe = TestProbe()
      val executor = TestProbe()
      val initiator = TestProbe()
      val runtime = TestProbe()
      val stream0 = TestProbe()
      val stream1 = TestProbe()
      val streams = Map (
        0 -> stream0.ref,
        1 -> stream1.ref
      )
      val env = Env(runtime = runtime.ref)

      val target = system.actorOf(Process.props(env, initiator.ref, executor.ref, streams, 1000))
      probe.send(target, Process.Commands.Start)
      executor.expectMsg(CommandsUnion.Commands.Run(target, streams))
    }

    "Undeploy process by ExecutorFinished message" in  {
      val probe = TestProbe()
      val executor = TestProbe()
      val initiator = TestProbe()
      val runtime = TestProbe()
      val stream0 = TestProbe()
      val stream1 = TestProbe()
      val streams = Map (
        0 -> stream0.ref,
        1 -> stream1.ref
      )
      val env = Env(runtime = runtime.ref)

      val target = system.actorOf(Process.props(env, initiator.ref, executor.ref, streams, 1000))
      target ! CommandsUnion.Responses.ExecutorFinished(0)
      stream0.expectMsg(Stream.Commands.FlushedClose)
      stream1.expectMsg(Stream.Commands.FlushedClose)
      runtime.expectMsg(Runtime.Commands.RemoveProcess(1000))
      initiator.expectMsg(Process.Responses.Stopped(0))
    }
  }

}
