package ru.serbis.okto.node.unit.runtime

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import akka.util.ByteString
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import ru.serbis.okto.node.log.{StdOutLogger, StreamLogger}
import ru.serbis.okto.node.runtime.{Stream, VmPool}
import ru.serbis.okto.node.runtime.Stream.Commands._
import ru.serbis.okto.node.runtime.Stream.Responses._

import scala.concurrent.duration._

class VmPoolSpec extends TestKit(ActorSystem("TestSystem")) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll with StreamLogger {

  override protected def beforeAll(): Unit = {
    initializeGlobalLogger(system)
    logger.addDestination(system.actorOf(StdOutLogger.props))
  }

  "VmPool" must {
    "Return free vm instance for Reserve message" in {
      val probe = TestProbe()
      val target = system.actorOf(VmPool.props(10, 3))

      probe.send(target, VmPool.Commands.Reserve)
      probe.expectMsgType[VmPool.Responses.VmInstance]
      probe.send(target, VmPool.Commands.GetPoolSize)
      probe.expectMsg(VmPool.Responses.PoolSize(3))
    }

    "Return PoolOverflow if max vm instances in the pool was reached" in {
      val probe = TestProbe()
      val target = system.actorOf(VmPool.props(2, 1))

      probe.send(target, VmPool.Commands.Reserve)
      probe.expectMsgType[VmPool.Responses.VmInstance]
      probe.send(target, VmPool.Commands.Reserve)
      probe.expectMsgType[VmPool.Responses.VmInstance]
      probe.send(target, VmPool.Commands.Reserve)
      probe.expectMsg(VmPool.Responses.PoolOverflow)
    }

    "Free vm instance by Free message" in {
      val probe = TestProbe()
      val target = system.actorOf(VmPool.props(3, 3))

      probe.send(target, VmPool.Commands.Reserve)
      val vm1 = probe.expectMsgType[VmPool.Responses.VmInstance]
      probe.send(target, VmPool.Commands.Reserve)
      val vm2 = probe.expectMsgType[VmPool.Responses.VmInstance]
      probe.send(target, VmPool.Commands.Reserve)
      val vm3 = probe.expectMsgType[VmPool.Responses.VmInstance]
      probe.send(target, VmPool.Commands.Reserve)
      probe.expectMsg(VmPool.Responses.PoolOverflow)
      probe.send(target, VmPool.Commands.Free(vm1.vm))
      probe.send(target, VmPool.Commands.Free(vm2.vm))
      probe.send(target, VmPool.Commands.Free(vm3.vm))
      probe.send(target, VmPool.Commands.Reserve)
      probe.send(target, VmPool.Commands.Reserve)
      probe.expectMsgType[VmPool.Responses.VmInstance]
    }

    "Inflate pool if insufficient vm instances was occurred" in {
      val probe = TestProbe()
      val target = system.actorOf(VmPool.props(2, 1))

      probe.send(target, VmPool.Commands.Reserve)
      probe.expectMsgType[VmPool.Responses.VmInstance]
      probe.send(target, VmPool.Commands.Reserve)
      probe.expectMsgType[VmPool.Responses.VmInstance]
    }

    "Deflate pool if some vm was freed and pool size > min and don't deflate if vise versa" in {
      val probe = TestProbe()
      val target = system.actorOf(VmPool.props(3, 1))

      probe.send(target, VmPool.Commands.Reserve)
      val vm1 = probe.expectMsgType[VmPool.Responses.VmInstance]
      probe.send(target, VmPool.Commands.Reserve)
      val vm2 = probe.expectMsgType[VmPool.Responses.VmInstance]
      probe.send(target, VmPool.Commands.Reserve)
      val vm3 = probe.expectMsgType[VmPool.Responses.VmInstance]

      probe.send(target, VmPool.Commands.GetPoolSize)
      probe.expectMsg(VmPool.Responses.PoolSize(3))

      probe.send(target, VmPool.Commands.Free(vm1.vm))
      probe.send(target, VmPool.Commands.GetPoolSize)
      probe.expectMsg(VmPool.Responses.PoolSize(2))

      probe.send(target, VmPool.Commands.Free(vm2.vm))
      probe.send(target, VmPool.Commands.GetPoolSize)
      probe.expectMsg(VmPool.Responses.PoolSize(1))

      probe.send(target, VmPool.Commands.Free(vm3.vm))
      probe.send(target, VmPool.Commands.GetPoolSize)
      probe.expectMsg(VmPool.Responses.PoolSize(1))
    }
  }
}
