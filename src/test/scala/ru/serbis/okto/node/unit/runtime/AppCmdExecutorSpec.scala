package ru.serbis.okto.node.unit.runtime

import javax.script.ScriptEngineManager
import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import akka.util.ByteString
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import ru.serbis.okto.node.common.{CommandsUnion, Env}
import ru.serbis.okto.node.log.{StdOutLogger, StreamLogger}
import ru.serbis.okto.node.reps.SyscomsRep.Responses.SystemCommandDefinition
import ru.serbis.okto.node.runtime.StreamControls.EOF
import ru.serbis.okto.node.runtime.app.AppCmdExecutor
import ru.serbis.okto.node.runtime.{Process, ProcessConstructor, Runtime, Stream, StreamControls, VmPool}
import ru.serbis.okto.node.common.ReachTypes.ReachByteString
import ru.serbis.okto.node.events.Eventer

class AppCmdExecutorSpec extends TestKit(ActorSystem("TestSystem")) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll with StreamLogger {

  override protected def beforeAll(): Unit = {
    initializeGlobalLogger(system)
    logger.addDestination(system.actorOf(StdOutLogger.props))
  }

  "AppCmdExecutor" must {
    "Process positive test" in {
      val eventer = TestProbe()
      val stream0 = TestProbe()
      val stream1 = TestProbe()
      val process = TestProbe()
      val vmPool = TestProbe()
      val env = Env(vmPool = vmPool.ref, system = Some(system), eventer = eventer.ref)
      val probe = TestProbe()
      val testScript =
        """
          function main(args) {
            stdOut.write(args[0]);
          }
        """
      val target = system.actorOf(AppCmdExecutor.props(env, Vector("cmd", testScript, "abc")))

      probe.send(target, CommandsUnion.Commands.Run(process.ref, Map(0 -> stream0.ref, 1 -> stream1.ref)))
      vmPool.expectMsg(VmPool.Commands.Reserve)
      vmPool.reply(VmPool.Responses.VmInstance(new ScriptEngineManager().getEngineByName("nashorn")))
      stream0.expectMsg(Stream.Commands.WriteWrapped(ByteString("abc") ++ ByteString(Array(StreamControls.EOI))))
      process.expectMsg(CommandsUnion.Responses.ExecutorFinished(0))
      eventer.expectMsg(Eventer.Commands.Unsubscribe(None, target))
    }

    "Stop process with exit code 1 if vm pool respond with PoolOverflow message" in {
      val eventer = TestProbe()
      val stream1 = TestProbe()
      val stream0 = TestProbe()
      val process = TestProbe()
      val vmPool = TestProbe()
      val env = Env(vmPool = vmPool.ref, eventer = eventer.ref)
      val probe = TestProbe()

      val target = system.actorOf(AppCmdExecutor.props(env, Vector("cmd", "", "abc"), testMode = true))

      probe.send(target, CommandsUnion.Commands.Run(process.ref, Map(0 -> stream0.ref, 1 -> stream1.ref)))
      vmPool.expectMsg(VmPool.Commands.Reserve)
      vmPool.reply(VmPool.Responses.PoolOverflow)
      stream0.expectMsg(Stream.Commands.WriteWrapped(ByteString("Unable to reserve vm runtime").eof.exit(1)))
      process.expectMsg(CommandsUnion.Responses.ExecutorFinished(1))
      eventer.expectMsg(Eventer.Commands.Unsubscribe(None, target))
    }

    "Stop process with exit code 2 if vm pool does not respond with expected timeout" in {
      val eventer = TestProbe()
      val stream1 = TestProbe()
      val stream0 = TestProbe()
      val process = TestProbe()
      val vmPool = TestProbe()
      val env = Env(vmPool = vmPool.ref, eventer = eventer.ref)
      val probe = TestProbe()

      val target = system.actorOf(AppCmdExecutor.props(env, Vector("cmd", "", "abc"), testMode = true))

      probe.send(target, CommandsUnion.Commands.Run(process.ref, Map(0 -> stream0.ref, 1 -> stream1.ref)))
      stream0.expectMsg(Stream.Commands.WriteWrapped(ByteString("Internal error 1").eof.exit(2)))
      process.expectMsg(CommandsUnion.Responses.ExecutorFinished(2))
      eventer.expectMsg(Eventer.Commands.Unsubscribe(None, target))
    }

    "Return error message to StdOut and terminate program, if script execution error was occurred" in {
      val eventer = TestProbe()
      val stream0 = TestProbe()
      val stream1 = TestProbe()
      val process = TestProbe()
      val vmPool = TestProbe()
      val env = Env(vmPool = vmPool.ref, system = Some(system), eventer = eventer.ref)
      val probe = TestProbe()
      val testScript =
        """
          function main(args) {
            some wrong code
          }
        """
      val target = system.actorOf(AppCmdExecutor.props(env, Vector("cmd", testScript, "abc")))

      probe.send(target, CommandsUnion.Commands.Run(process.ref, Map(0 -> stream0.ref, 1 -> stream1.ref)))
      vmPool.expectMsg(VmPool.Commands.Reserve)
      vmPool.reply(VmPool.Responses.VmInstance(new ScriptEngineManager().getEngineByName("nashorn")))
      val msg = stream0.expectMsgType[Stream.Commands.WriteWrapped].data.utf8String
      stream0.expectMsg(Stream.Commands.WriteWrapped(ByteString().eof.exit(3)))
      process.expectMsg(CommandsUnion.Responses.ExecutorFinished(3))
      eventer.expectMsg(Eventer.Commands.Unsubscribe(None, target))
    }

    "Being in ScriptExecution state" should {
      "Write data to command StdOut by StdOutWrite message" in {
        val eventer = TestProbe()
        val stream0 = TestProbe()
        val stream1 = TestProbe()
        val process = TestProbe()
        val vmPool = TestProbe()
        val env = Env(vmPool = vmPool.ref, system = Some(system), eventer = eventer.ref)
        val probe = TestProbe()
        val testScript ="""function main(args) { while(true) {} }"""

        val target = system.actorOf(AppCmdExecutor.props(env, Vector("cmd", testScript)))
        probe.send(target, CommandsUnion.Commands.Run(process.ref, Map(0 -> stream0.ref, 1 -> stream1.ref)))
        vmPool.expectMsg(VmPool.Commands.Reserve)
        vmPool.reply(VmPool.Responses.VmInstance(new ScriptEngineManager().getEngineByName("nashorn")))

        probe.send(target, AppCmdExecutor.Commands.StdOutWrite(ByteString("abc")))
        stream0.expectMsg(Stream.Commands.WriteWrapped(ByteString("abc")))
      }

      "Perform program termination by Exit message" in {
        val eventer = TestProbe()
        val stream0 = TestProbe()
        val stream1 = TestProbe()
        val process = TestProbe()
        val vmPool = TestProbe()
        val env = Env(vmPool = vmPool.ref, system = Some(system), eventer = eventer.ref)
        val probe = TestProbe()
        val testScript ="""function main(args) { while(true) {} }"""

        val target = system.actorOf(AppCmdExecutor.props(env, Vector("cmd", testScript)))
        probe.send(target, CommandsUnion.Commands.Run(process.ref, Map(0 -> stream0.ref, 1 -> stream1.ref)))
        vmPool.expectMsg(VmPool.Commands.Reserve)
        val vm = new ScriptEngineManager().getEngineByName("nashorn")
        vmPool.reply(VmPool.Responses.VmInstance(vm))
        probe.send(target, AppCmdExecutor.Commands.Exit(10))
        vmPool.expectMsg(VmPool.Commands.Free(vm))
        stream0.expectMsg(Stream.Commands.WriteWrapped(ByteString().eof.exit(10)))
        process.expectMsg(CommandsUnion.Responses.ExecutorFinished(10))
        eventer.expectMsg(Eventer.Commands.Unsubscribe(None, target))
      }

      "For CreateLocalShell message" should {
        "Create new shell process and return it definition" in {
          val eventer = TestProbe()
          val stream0 = TestProbe()
          val stream1 = TestProbe()
          val process = TestProbe()
          val runtime = TestProbe()
          val shellProcess = TestProbe()
          val shellStdIn = TestProbe()
          val shellStdOut = TestProbe()
          val executor = TestProbe()

          val vmPool = TestProbe()
          val env = Env(vmPool = vmPool.ref, runtime = runtime.ref, system = Some(system), eventer = eventer.ref)
          val probe = TestProbe()
          val testScript ="""function main(args) { while(true) {} }"""

          val target = system.actorOf(AppCmdExecutor.props(env, Vector("cmd", testScript)))
          probe.send(target, CommandsUnion.Commands.Run(process.ref, Map(0 -> stream0.ref, 1 -> stream1.ref)))
          vmPool.expectMsg(VmPool.Commands.Reserve)
          vmPool.reply(VmPool.Responses.VmInstance(new ScriptEngineManager().getEngineByName("nashorn")))

          probe.send(target, AppCmdExecutor.Commands.CreateLocalShell())
          runtime.expectMsg(Runtime.Commands.Spawn("shell", Vector.empty, SystemCommandDefinition(""), target, "script"))
          runtime.reply(ProcessConstructor.Responses.ProcessDef(shellProcess.ref, executor.ref, 1000, Map(0 -> shellStdOut.ref, 1 -> shellStdIn.ref), 0, "", "", ("root", 0)))
          shellProcess.expectMsg(Process.Commands.Start)
          probe.expectMsg(AppCmdExecutor.Responses.ShellDefinition(shellProcess.ref, shellStdIn.ref, shellStdOut.ref))
        }

        "Return ShellCreationError if spawn error was occurred" in {
          val eventer = TestProbe()
          val stream0 = TestProbe()
          val stream1 = TestProbe()
          val process = TestProbe()
          val runtime = TestProbe()

          val vmPool = TestProbe()
          val env = Env(vmPool = vmPool.ref, runtime = runtime.ref, system = Some(system), eventer = eventer.ref)
          val probe = TestProbe()
          val testScript ="""function main(args) { while(true) {} }"""

          val target = system.actorOf(AppCmdExecutor.props(env, Vector("cmd", testScript)))
          probe.send(target, CommandsUnion.Commands.Run(process.ref, Map(0 -> stream0.ref, 1 -> stream1.ref)))
          vmPool.expectMsg(VmPool.Commands.Reserve)
          vmPool.reply(VmPool.Responses.VmInstance(new ScriptEngineManager().getEngineByName("nashorn")))

          probe.send(target, AppCmdExecutor.Commands.CreateLocalShell())
          runtime.expectMsg(Runtime.Commands.Spawn("shell", Vector.empty, SystemCommandDefinition(""), target, "script"))
          runtime.reply(Runtime.Responses.SpawnError)
          probe.expectMsg(AppCmdExecutor.Responses.ShellCreationError)
        }

        "Return ShellCreationError if runtime does not respond with expected timeout" in {
          val eventer = TestProbe()
          val stream0 = TestProbe()
          val stream1 = TestProbe()
          val process = TestProbe()
          val runtime = TestProbe()

          val vmPool = TestProbe()
          val env = Env(vmPool = vmPool.ref, runtime = runtime.ref, system = Some(system), eventer = eventer.ref)
          val probe = TestProbe()
          val testScript ="""function main(args) { while(true) {} }"""

          val target = system.actorOf(AppCmdExecutor.props(env, Vector("cmd", testScript), testMode = true))
          probe.send(target, CommandsUnion.Commands.Run(process.ref, Map(0 -> stream0.ref, 1 -> stream1.ref)))
          vmPool.expectMsg(VmPool.Commands.Reserve)
          vmPool.reply(VmPool.Responses.VmInstance(new ScriptEngineManager().getEngineByName("nashorn")))

          probe.send(target, AppCmdExecutor.Commands.CreateLocalShell())
          probe.expectMsg(AppCmdExecutor.Responses.ShellCreationError)
        }
      }
    }
  }
}
