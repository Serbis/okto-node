package ru.serbis.okto.node.unit.syscoms.proc

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import ru.serbis.okto.node.common.Env
import ru.serbis.okto.node.log.{StdOutLogger, StreamLogger}
import ru.serbis.okto.node.runtime.Runtime
import ru.serbis.okto.node.syscoms.proc.{Proc, ProcSig}

class ProcSigSpec extends TestKit(ActorSystem("TestSystem")) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll with StreamLogger {


  override protected def beforeAll(): Unit = {
    initializeGlobalLogger(system)
    logger.addDestination(system.actorOf(StdOutLogger.props))
  }


  "ProcSig" must {
    "Process positive test" in {
      val probe = TestProbe()
      val runtime = TestProbe()
      val stdOut = TestProbe()
      val stdIn = TestProbe()
      val env = Env(runtime = runtime.ref)
      val target = system.actorOf(ProcSig.props(Vector("100", "9"), env, stdIn.ref, stdOut.ref))

      probe.send(target, ProcSig.Commands.Exec)
      runtime.expectMsg(Runtime.Commands.SendSignal(100, 9))
      runtime.reply(Runtime.Responses.SignalSent)
      probe.expectMsg(Proc.Internals.Complete(0, "OK"))
    }

    "Complete with error code if options errors was occurred" in {
      val probe = TestProbe()
      val runtime = TestProbe()
      val stdOut = TestProbe()
      val stdIn = TestProbe()
      val env = Env(runtime = runtime.ref)

      val target1 = system.actorOf(ProcSig.props(Vector("x", "9"), env, stdIn.ref, stdOut.ref))
      probe.send(target1, ProcSig.Commands.Exec)
      probe.expectMsg(Proc.Internals.Complete(20, "Pid and signal must be a numbers"))

      val target2 = system.actorOf(ProcSig.props(Vector("100", "x"), env, stdIn.ref, stdOut.ref))
      probe.send(target2, ProcSig.Commands.Exec)
      probe.expectMsg(Proc.Internals.Complete(20, "Pid and signal must be a numbers"))

      val target3 = system.actorOf(ProcSig.props(Vector("100"), env, stdIn.ref, stdOut.ref))
      probe.send(target3, ProcSig.Commands.Exec)
      probe.expectMsg(Proc.Internals.Complete(21, "Expected 2 option but found 1"))
    }

    "Complete with error code if rimtime respond with ProcessNotRegistered" in {
      val probe = TestProbe()
      val runtime = TestProbe()
      val stdOut = TestProbe()
      val stdIn = TestProbe()
      val env = Env(runtime = runtime.ref)
      val target = system.actorOf(ProcSig.props(Vector("100", "9"), env, stdIn.ref, stdOut.ref))

      probe.send(target, ProcSig.Commands.Exec)
      runtime.expectMsg(Runtime.Commands.SendSignal(100, 9))
      runtime.reply(Runtime.Responses.ProcessNotRegistered)
      probe.expectMsg(Proc.Internals.Complete(22, "Process does not exist"))
    }

    "Return error if runtime does not respond with expected timeout" in {
      val probe = TestProbe()
      val runtime = TestProbe()
      val stdOut = TestProbe()
      val stdIn = TestProbe()
      val env = Env(runtime = runtime.ref)
      val target = system.actorOf(ProcSig.props(Vector("100", "9"), env, stdIn.ref, stdOut.ref, testMode = true))
      probe.send(target, ProcSig.Commands.Exec)
      probe.expectMsg(Proc.Internals.Complete(23, "Runtime timeout"))
    }
  }
}