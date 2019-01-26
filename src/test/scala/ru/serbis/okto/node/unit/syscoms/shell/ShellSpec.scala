package ru.serbis.okto.node.unit.syscoms.shell

import java.io.File

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import akka.util.ByteString
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import ru.serbis.okto.node.common.{CommandsUnion, Env}
import ru.serbis.okto.node.log.{StdOutLogger, StreamLogger}
import ru.serbis.okto.node.syscoms.shell.{PipePreparator, Shell}
import ru.serbis.okto.node.runtime.StreamControls._
import ru.serbis.okto.node.syscoms.shell.StatementsParser.CommandNode
import ru.serbis.okto.node.testut.TestActorSystem
import ru.serbis.okto.node.runtime.Stream

class ShellSpec extends TestKit(ActorSystem("TestSystem")) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll with StreamLogger {


  override protected def beforeAll(): Unit = {
    initializeGlobalLogger(system)
    logger.addDestination(system.actorOf(StdOutLogger.props))
  }


  "Shell" must {
    "Process positive test for piped statement" in {
      val probe = TestProbe()
      val shellStdOut = TestProbe()
      val shellStdIn = TestProbe()
      val process = TestProbe()
      val pipePreparator = TestProbe()
      val pipeStdIn = TestProbe()
      val pipeStdOut = TestProbe()
      val exSystem = new TestActorSystem({
        case Props(_, _, args) if args.head.asInstanceOf[Class[_ <: Actor]].getName == PipePreparator.getClass.getName.dropRight(1) => pipePreparator.ref
      })
      val target = system.actorOf(Shell.props(Env(), Vector.empty, exSystem))

      probe.send(target, CommandsUnion.Commands.Run(process.ref,  Map(0 -> shellStdOut.ref, 1 -> shellStdIn.ref)))
      shellStdOut.expectMsg(Stream.Commands.WriteWrapped(ByteString(Array(PROMPT))))
      shellStdIn.send(target, Stream.Responses.Data(ByteString("a | b | c") ++ ByteString(Array(EOI))))
      pipePreparator.expectMsg(PipePreparator.Commands.Exec(List(CommandNode("a", Vector.empty), CommandNode("b", Vector.empty), CommandNode("c", Vector.empty))))
      pipePreparator.reply(PipePreparator.Responses.PipeCircuit(pipeStdIn.ref, pipeStdOut.ref))
      pipeStdOut.expectMsg(Stream.Commands.Attach(target))
      pipeStdOut.send(target, Stream.Responses.Data(ByteString("x") ++ ByteString(Array(EOI))))
      shellStdOut.expectMsg(Stream.Commands.WriteWrapped(ByteString("x") ++ ByteString(Array(EOI))))
      pipeStdOut.reply(Stream.Responses.Attached)
      pipeStdOut.expectMsg(Stream.Commands.Flush)
      shellStdIn.send(target, Stream.Responses.Data(ByteString("y") ++ ByteString(Array(EOI))))
      pipeStdIn.expectMsg(Stream.Commands.WriteWrapped(ByteString("y") ++ ByteString(Array(EOI))))
      pipeStdOut.send(target, Stream.Responses.Data(ByteString("z") ++ ByteString(Array(EOF))))
      shellStdOut.expectMsg(Stream.Commands.WriteWrapped(ByteString("z") ++ ByteString(Array(EOP))))
      shellStdOut.expectMsg(Stream.Commands.WriteWrapped(ByteString(Array(PROMPT))))
    }




    "After start" should {
      "Write prompt to StdOut" in {
        val probe = TestProbe()
        val shellStdOut = TestProbe()
        val shellStdIn = TestProbe()
        val process = TestProbe()
        val target = system.actorOf(Shell.props(Env(), Vector.empty))

        probe.send(target, CommandsUnion.Commands.Run(process.ref,  Map(0 -> shellStdOut.ref, 1 -> shellStdIn.ref)))
        shellStdOut.expectMsg(Stream.Commands.WriteWrapped(ByteString(Array(PROMPT))))
      }
    }



    "In command mode" should {
      "Return 'Wrong statement' for syntactically incorrect input" in {
        val probe = TestProbe()
        val shellStdOut = TestProbe()
        val shellStdIn = TestProbe()
        val process = TestProbe()
        val target = system.actorOf(Shell.props(Env(), Vector.empty))

        probe.send(target, CommandsUnion.Commands.Run(process.ref,  Map(0 -> shellStdOut.ref, 1 -> shellStdIn.ref)))
        shellStdOut.expectMsg(Stream.Commands.WriteWrapped(ByteString(Array(PROMPT))))
        shellStdIn.send(target, Stream.Responses.Data(ByteString(" ") ++ ByteString(Array(EOI))))
        shellStdOut.expectMsg(Stream.Commands.WriteWrapped(ByteString("Wrong statement") ++ ByteString(Array(EOI, EOF, PROMPT))))
      }

      "Buffering data in command mode before receiving the EOI character" in {
        val probe = TestProbe()
        val shellStdOut = TestProbe()
        val shellStdIn = TestProbe()
        val process = TestProbe()
        val target = system.actorOf(Shell.props(Env(), Vector.empty))

        probe.send(target, CommandsUnion.Commands.Run(process.ref,  Map(0 -> shellStdOut.ref, 1 -> shellStdIn.ref)))
        shellStdOut.expectMsg(Stream.Commands.WriteWrapped(ByteString(Array(PROMPT))))
        shellStdIn.send(target, Stream.Responses.Data(ByteString(" ")))
        shellStdIn.send(target, Stream.Responses.Data(ByteString(" ")))
        shellStdIn.send(target, Stream.Responses.Data(ByteString(Array(EOI))))
        shellStdOut.expectMsg(Stream.Commands.WriteWrapped(ByteString("Wrong statement") ++ ByteString(Array(EOI, EOF, PROMPT))))
        shellStdIn.send(target, Stream.Responses.Data(ByteString(" ")))
        shellStdIn.send(target, Stream.Responses.Data(ByteString(" ") ++ ByteString(Array(EOI))))
        shellStdOut.expectMsg(Stream.Commands.WriteWrapped(ByteString("Wrong statement") ++ ByteString(Array(EOI, EOF, PROMPT))))
      }

      "Stop shell after receive EOF character" in {
        val probe = TestProbe()
        val shellStdOut = TestProbe()
        val shellStdIn = TestProbe()
        val process = TestProbe()
        val target = system.actorOf(Shell.props(Env(), Vector.empty))

        probe.send(target, CommandsUnion.Commands.Run(process.ref,  Map(0 -> shellStdOut.ref, 1 -> shellStdIn.ref)))
        shellStdOut.expectMsg(Stream.Commands.WriteWrapped(ByteString(Array(PROMPT))))
        shellStdIn.send(target, Stream.Responses.Data(ByteString(Array(EOF))))
        process.expectMsg(CommandsUnion.Responses.ExecutorFinished(0))
      }

      "Stop shell after idle timeout" in {
        val probe = TestProbe()
        val shellStdOut = TestProbe()
        val shellStdIn = TestProbe()
        val process = TestProbe()
        val target = system.actorOf(Shell.props(Env(), Vector.empty, testMode = true))

        probe.send(target, CommandsUnion.Commands.Run(process.ref,  Map(0 -> shellStdOut.ref, 1 -> shellStdIn.ref)))
        shellStdOut.expectMsg(Stream.Commands.WriteWrapped(ByteString(Array(PROMPT))))
        shellStdOut.expectMsg(Stream.Commands.WriteWrapped(ByteString(Array(EOF))))
        process.expectMsg(CommandsUnion.Responses.ExecutorFinished(0))
      }
    }

    "In statements processing mode" should {
      //No tests
    }

    "In statement pipe init mode" should {
      "For CommandsNotFound response print corresponding message to the shell stdOut and goto to command mode" in {
        val probe = TestProbe()
        val shellStdOut = TestProbe()
        val shellStdIn = TestProbe()
        val process = TestProbe()
        val pipePreparator = TestProbe()
        val exSystem = new TestActorSystem({
          case Props(_, _, args) if args.head.asInstanceOf[Class[_ <: Actor]].getName == PipePreparator.getClass.getName.dropRight(1) => pipePreparator.ref
        })
        val target = system.actorOf(Shell.props(Env(), Vector.empty, exSystem))

        probe.send(target, CommandsUnion.Commands.Run(process.ref,  Map(0 -> shellStdOut.ref, 1 -> shellStdIn.ref)))
        shellStdOut.expectMsg(Stream.Commands.WriteWrapped(ByteString(Array(PROMPT))))
        shellStdIn.send(target, Stream.Responses.Data(ByteString("a | b | c") ++ ByteString(Array(EOI))))
        pipePreparator.expectMsg(PipePreparator.Commands.Exec(List(CommandNode("a", Vector.empty), CommandNode("b", Vector.empty), CommandNode("c", Vector.empty))))
        pipePreparator.reply(PipePreparator.Responses.CommandsNotFound(Set("a", "c")))
        shellStdOut.expectMsg(Stream.Commands.WriteWrapped(ByteString(s"Commands not found - a, c") ++ ByteString(Array(EOI, EOP, 16.toByte))))
        shellStdOut.expectMsg(Stream.Commands.WriteWrapped(ByteString(Array(PROMPT))))
      }

      "For InternalError response print corresponding message to the shell stdOut and goto to command mode (print prompt)" in {
        val probe = TestProbe()
        val shellStdOut = TestProbe()
        val shellStdIn = TestProbe()
        val process = TestProbe()
        val pipePreparator = TestProbe()
        val exSystem = new TestActorSystem({
          case Props(_, _, args) if args.head.asInstanceOf[Class[_ <: Actor]].getName == PipePreparator.getClass.getName.dropRight(1) => pipePreparator.ref
        })
        val target = system.actorOf(Shell.props(Env(), Vector.empty, exSystem))

        probe.send(target, CommandsUnion.Commands.Run(process.ref,  Map(0 -> shellStdOut.ref, 1 -> shellStdIn.ref)))
        shellStdOut.expectMsg(Stream.Commands.WriteWrapped(ByteString(Array(PROMPT))))
        shellStdIn.send(target, Stream.Responses.Data(ByteString("a | b | c") ++ ByteString(Array(EOI))))
        pipePreparator.expectMsg(PipePreparator.Commands.Exec(List(CommandNode("a", Vector.empty), CommandNode("b", Vector.empty), CommandNode("c", Vector.empty))))
        pipePreparator.reply(PipePreparator.Responses.InternalError)
        shellStdOut.expectMsg(Stream.Commands.WriteWrapped(ByteString(s"Unable to execute statements due to internal error 2") ++ ByteString(Array(EOI, EOP, 17.toByte))))
        shellStdOut.expectMsg(Stream.Commands.WriteWrapped(ByteString(Array(PROMPT))))
      }

      "Write internal error to stdOut if PipePreparator does not respond with expected timeout" in {
        val probe = TestProbe()
        val shellStdOut = TestProbe()
        val shellStdIn = TestProbe()
        val process = TestProbe()
        val pipePreparator = TestProbe()
        val exSystem = new TestActorSystem({
          case Props(_, _, args) if args.head.asInstanceOf[Class[_ <: Actor]].getName == PipePreparator.getClass.getName.dropRight(1) => pipePreparator.ref
        })
        val target = system.actorOf(Shell.props(Env(), Vector.empty, exSystem, testMode = true))

        probe.send(target, CommandsUnion.Commands.Run(process.ref,  Map(0 -> shellStdOut.ref, 1 -> shellStdIn.ref)))
        shellStdOut.expectMsg(Stream.Commands.WriteWrapped(ByteString(Array(PROMPT))))
        shellStdIn.send(target, Stream.Responses.Data(ByteString("a | b | c") ++ ByteString(Array(EOI))))
        shellStdOut.expectMsg(Stream.Commands.WriteWrapped(ByteString("Unable to execute statements due to internal error 3") ++ ByteString(Array(EOI, EOP, 0.toByte))))
        shellStdOut.expectMsg(Stream.Commands.WriteWrapped(ByteString(Array(PROMPT))))
      }
    }

    "In statements pipe attach mode" should {
      "Write IO timeout to stdOut if IO operations timeout was reached" in {
        val probe = TestProbe()
        val shellStdOut = TestProbe()
        val shellStdIn = TestProbe()
        val process = TestProbe()
        val pipePreparator = TestProbe()
        val pipeStdIn = TestProbe()
        val pipeStdOut = TestProbe()
        val exSystem = new TestActorSystem({
          case Props(_, _, args) if args.head.asInstanceOf[Class[_ <: Actor]].getName == PipePreparator.getClass.getName.dropRight(1) => pipePreparator.ref
        })
        val target = system.actorOf(Shell.props(Env(), Vector.empty, exSystem, testMode = true))

        probe.send(target, CommandsUnion.Commands.Run(process.ref,  Map(0 -> shellStdOut.ref, 1 -> shellStdIn.ref)))
        shellStdOut.expectMsg(Stream.Commands.WriteWrapped(ByteString(Array(PROMPT))))
        shellStdIn.send(target, Stream.Responses.Data(ByteString("a | b | c") ++ ByteString(Array(EOI))))
        pipePreparator.expectMsg(PipePreparator.Commands.Exec(List(CommandNode("a", Vector.empty), CommandNode("b", Vector.empty), CommandNode("c", Vector.empty))))
        pipePreparator.reply(PipePreparator.Responses.PipeCircuit(pipeStdIn.ref, pipeStdOut.ref))
        shellStdOut.expectMsg(Stream.Commands.WriteWrapped(ByteString("IO operations timeout") ++ ByteString(Array(EOI, EOF))))
        shellStdOut.expectMsg(Stream.Commands.WriteWrapped(ByteString(Array(PROMPT))))
      }
    }


  }
}
