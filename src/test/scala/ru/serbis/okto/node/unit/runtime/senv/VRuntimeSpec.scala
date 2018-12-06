package ru.serbis.okto.node.unit.runtime.senv

import java.util.Date

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import ru.serbis.okto.node.log.{StdOutLogger, StreamLogger}
import ru.serbis.okto.node.runtime.AppCmdExecutor
import ru.serbis.okto.node.runtime.senv.VRuntime

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future}

class VRuntimeSpec extends TestKit(ActorSystem("TestSystem")) with ImplicitSender with WordSpecLike with Matchers with BeforeAndAfterAll with StreamLogger {

  override protected def beforeAll(): Unit = {
    initializeGlobalLogger(system)
    logger.addDestination(system.actorOf(StdOutLogger.props))
  }

  "VRuntime" must {
    "By createLocalShell method" should {
      "Send CreateLocalShell to the executor and create new VShell object based on it response" in {
        val executor = TestProbe()
        val process = TestProbe()
        val stdIn = TestProbe()
        val stdOut = TestProbe()
        val target = new VRuntime(executor.ref)

        val shellFut = Future { target.createLocalShell() }
        executor.expectMsg(AppCmdExecutor.Commands.CreateLocalShell())
        executor.reply(AppCmdExecutor.Responses.ShellDefinition(process.ref, stdIn.ref, stdOut.ref))
        val result = Await.result(shellFut, 1 second)
        result.process shouldEqual process.ref
        result.stdIn shouldEqual stdIn.ref
        result.stdOut shouldEqual stdOut.ref
      }

      "Return null if executor does not respond with expected timeout" in {
        val executor = TestProbe()
        val target = new VRuntime(executor.ref)

        val shellFut = Future { target.createLocalShell() }
        executor.expectMsg(AppCmdExecutor.Commands.CreateLocalShell())
        val result = Await.result(shellFut, 5 second)
        result shouldEqual null
      }

      "Return null if executor respond with unexpected message" in {
        val executor = TestProbe()
        val target = new VRuntime(executor.ref)

        val shellFut = Future { target.createLocalShell() }
        executor.expectMsg(AppCmdExecutor.Commands.CreateLocalShell())
        executor.reply("X")
        val result = Await.result(shellFut, 5 second)
        result shouldEqual null
      }
    }

    "By sleep method" should {
      "Sleep current thread for specified ms" in {
        val executor = TestProbe()
        val target = new VRuntime(executor.ref)

        val startTime = System.currentTimeMillis()

        target.sleep(500)
        System.currentTimeMillis() > startTime + 495 shouldEqual true
      }
    }
  }
}