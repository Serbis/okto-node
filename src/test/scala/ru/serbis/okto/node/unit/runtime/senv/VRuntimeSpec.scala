package ru.serbis.okto.node.unit.runtime.senv

import java.util.Date

import akka.actor.{ActorRef, ActorSystem}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import ru.serbis.okto.node.log.{StdOutLogger, StreamLogger}
import ru.serbis.okto.node.reps.ScriptsRep
import ru.serbis.okto.node.runtime.app.AppCmdExecutor
import ru.serbis.okto.node.runtime.senv.vruntime.VRuntime

import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.{Await, Future, Promise}

class VRuntimeSpec extends TestKit(ActorSystem("TestSystem")) with ImplicitSender with WordSpecLike with Matchers with BeforeAndAfterAll with StreamLogger {

  override protected def beforeAll(): Unit = {
    initializeGlobalLogger(system)
    logger.addDestination(system.actorOf(StdOutLogger.props))
  }

  "VRuntime" must {
    "By import method" should {
      "Request script code from repository and return namespace from it" in {
        val executor = TestProbe()
        val scriptsRep = TestProbe()
        val target = new VRuntime(executor.ref, scriptsRep.ref)

        val fut = Future { target.`import`("foo", "b") }
        scriptsRep.expectMsg(ScriptsRep.Commands.GetScript("foo.js"))
        scriptsRep.reply(ScriptsRep.Responses.Script("_export a\n    var ax = 10;\nexport_\n\n_export b\n    var bx = 10;\nexport_\n\n_export c\n    var cx = 10;\nexport_"))
        Await.result(fut, 5 second) shouldEqual "\n    var bx = 10;\n"
      }

      "Request script code from repository and return all namespaces from it for astrix ns" in {
        val executor = TestProbe()
        val scriptsRep = TestProbe()
        val target = new VRuntime(executor.ref, scriptsRep.ref)

        val fut = Future { target.`import`("foo", "*") }
        scriptsRep.expectMsg(ScriptsRep.Commands.GetScript("foo.js"))
        scriptsRep.reply(ScriptsRep.Responses.Script("_export a\n    var ax = 10;\nexport_\n\n_export b\n    var bx = 10;\nexport_\n\n_export c\n    var cx = 10;\nexport_"))
        Await.result(fut, 5 second) shouldEqual "\n    var ax = 10;\n,\n    var bx = 10;\n,\n    var cx = 10;\n"
      }

      "Request script code from repository and cache all namespaces for future usage" in {
        val executor = TestProbe()
        val scriptsRep = TestProbe()
        val target = new VRuntime(executor.ref, scriptsRep.ref)

        val fut = Future { target.`import`("foo", "b") }
        scriptsRep.expectMsg(ScriptsRep.Commands.GetScript("foo.js"))
        scriptsRep.reply(ScriptsRep.Responses.Script("_export a\n    var ax = 10;\nexport_\n\n_export b\n    var bx = 10;\nexport_\n\n_export c\n    var cx = 10;\nexport_"))
        Await.result(fut, 5 second) shouldEqual "\n    var bx = 10;\n"
        val fut2 = Future { target.`import`("foo", "b") }
        Await.result(fut2, 5 second) shouldEqual "\n    var bx = 10;\n"
      }

      "Request script code from repository and return error if namespace does not exist" in {
        val executor = TestProbe()
        val scriptsRep = TestProbe()
        val target = new VRuntime(executor.ref, scriptsRep.ref)

        val fut = Future { target.`import`("foo", "z") }
        scriptsRep.expectMsg(ScriptsRep.Commands.GetScript("foo.js"))
        scriptsRep.reply(ScriptsRep.Responses.Script("_export a\n    var ax = 10;\nexport_\n\n_export b\n    var bx = 10;\nexport_\n\n_export c\n    var cx = 10;\nexport_"))
        Await.result(fut, 5 second) shouldEqual "lib_foo_ns_z_not_found"
      }

      "Request script code from repository and return error if script does not exist" in {
        val executor = TestProbe()
        val scriptsRep = TestProbe()
        val target = new VRuntime(executor.ref, scriptsRep.ref)

        val fut = Future { target.`import`("foo", "z") }
        scriptsRep.expectMsg(ScriptsRep.Commands.GetScript("foo.js"))
        scriptsRep.reply(ScriptsRep.Responses.ScriptNotFound)
        Await.result(fut, 5 second) shouldEqual "lib_foo_not_found"
      }

      "Request script code from repository and return error if repository respond with bad message" in {
        val executor = TestProbe()
        val scriptsRep = TestProbe()
        val target = new VRuntime(executor.ref, scriptsRep.ref)

        val fut = Future { target.`import`("foo", "z") }
        scriptsRep.expectMsg(ScriptsRep.Commands.GetScript("foo.js"))
        scriptsRep.reply(1)
        Await.result(fut, 5 second) shouldEqual "lib_foo_internal_error"
      }
      "Request script code from repository and return error if repository does not respond" in {
        val executor = TestProbe()
        val scriptsRep = TestProbe()
        val target = new VRuntime(executor.ref, scriptsRep.ref, tm = true)

        val fut = Future { target.`import`("foo", "z") }
        scriptsRep.expectMsg(ScriptsRep.Commands.GetScript("foo.js"))
        Await.result(fut, 5 second) shouldEqual "lib_foo_rep_timeout"
      }

    }

    "By createLocalShell method" should {
      "Send CreateLocalShell to the executor and create new VShell object based on it response" in {
        val executor = TestProbe()
        val process = TestProbe()
        val stdIn = TestProbe()
        val stdOut = TestProbe()
        val target = new VRuntime(executor.ref, ActorRef.noSender)

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
        val target = new VRuntime(executor.ref, ActorRef.noSender)

        val shellFut = Future { target.createLocalShell() }
        executor.expectMsg(AppCmdExecutor.Commands.CreateLocalShell())
        val result = Await.result(shellFut, 5 second)
        result shouldEqual null
      }

      "Return null if executor respond with unexpected message" in {
        val executor = TestProbe()
        val target = new VRuntime(executor.ref, ActorRef.noSender)

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
        val target = new VRuntime(executor.ref, ActorRef.noSender)

        val startTime = System.currentTimeMillis()

        target.sleep(500)
        System.currentTimeMillis() > startTime + 495 shouldEqual true
      }

      "Break up sleep if thread interruption was occurs" in {
        val executor = TestProbe()
        val target = new VRuntime(executor.ref, ActorRef.noSender)

        val p = Promise[Long]
        val t = new Thread(() => p.success(target.sleep(1000)))
        t.start()
        Thread.sleep(200)
        t.interrupt()
        val r = Await.result(p.future, 1 second)
        r <= 1000 - 200 shouldEqual true
      }
    }

    "By sleep method must set interrupt lock and sleep current thread for specified ms" in {
      val executor = TestProbe()
      val target = new VRuntime(executor.ref, ActorRef.noSender)

      val fut = Future {
        target.sleepc(500)
      }
      Thread.sleep(200)
      target.mayInterrupted.get shouldEqual false
      Await.result(fut, 1 second)
      target.mayInterrupted.get shouldEqual true
    }

    "By hasSig method must return new signals status" in {
      val executor = TestProbe()
      val target = new VRuntime(executor.ref, ActorRef.noSender)

      target.hasSig() shouldEqual false
      target.sigQueue.offer(100)
      target.hasSig() shouldEqual true
    }

    "By sig method must return new signal from queue or 0" in {
      val executor = TestProbe()
      val target = new VRuntime(executor.ref, ActorRef.noSender)

      target.sig() shouldEqual 0
      target.sigQueue.offer(100)
      target.sig() shouldEqual 100
    }
  }
}