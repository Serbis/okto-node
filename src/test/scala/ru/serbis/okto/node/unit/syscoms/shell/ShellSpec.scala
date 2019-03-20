package ru.serbis.okto.node.unit.syscoms.shell

import java.io.File

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import akka.util.ByteString
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import ru.serbis.okto.node.access.AccessCredentials.UserCredentials
import ru.serbis.okto.node.common.{CommandsUnion, Env}
import ru.serbis.okto.node.log.{StdOutLogger, StreamLogger}
import ru.serbis.okto.node.syscoms.shell.{PipePreparator, Shell, SimpleAuthenticator}
import ru.serbis.okto.node.runtime.StreamControls._
import ru.serbis.okto.node.syscoms.shell.StatementsParser.CommandNode
import ru.serbis.okto.node.testut.TestActorSystem
import ru.serbis.okto.node.runtime.Stream
import ru.serbis.okto.node.common.ReachTypes.ReachByteString

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
      pipePreparator.expectMsg(PipePreparator.Commands.Exec(UserCredentials("nobody"), List(CommandNode("a", Vector.empty), CommandNode("b", Vector.empty), CommandNode("c", Vector.empty))))
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
        shellStdOut.expectMsg(Stream.Commands.WriteWrapped(ByteString("Wrong statement").eoi.eop.exit(1000)))
      }

      "Run SimpleAuthenticator for auth simple input" in {
        val probe = TestProbe()
        val shellStdOut = TestProbe()
        val shellStdIn = TestProbe()
        val process = TestProbe()
        val simpleAuthenticator = TestProbe()
        val exSystem = new TestActorSystem({
          case Props(_, _, args) if args.head.asInstanceOf[Class[_ <: Actor]].getName == SimpleAuthenticator.getClass.getName.dropRight(1) => simpleAuthenticator.ref
        })
        val target = system.actorOf(Shell.props(Env(), Vector.empty, exSystem))

        probe.send(target, CommandsUnion.Commands.Run(process.ref,  Map(0 -> shellStdOut.ref, 1 -> shellStdIn.ref)))
        shellStdOut.expectMsg(Stream.Commands.WriteWrapped(ByteString(Array(PROMPT))))
        shellStdIn.send(target, Stream.Responses.Data(ByteString("auth simple root 123456") ++ ByteString(Array(EOI))))
        simpleAuthenticator.expectMsg(SimpleAuthenticator.Commands.Exec(Vector("root", "123456")))
      }

      "Return code 1201 auth command has bad type" in {
        val probe = TestProbe()
        val shellStdOut = TestProbe()
        val shellStdIn = TestProbe()
        val process = TestProbe()
        val target = system.actorOf(Shell.props(Env(), Vector.empty))

        probe.send(target, CommandsUnion.Commands.Run(process.ref,  Map(0 -> shellStdOut.ref, 1 -> shellStdIn.ref)))
        shellStdOut.expectMsg(Stream.Commands.WriteWrapped(ByteString(Array(PROMPT))))
        shellStdIn.send(target, Stream.Responses.Data(ByteString("auth xxx") ++ ByteString(Array(EOI))))
        shellStdOut.expectMsg(Stream.Commands.WriteWrapped(ByteString("Unknown auth type").eoi.eop.exit(1201)))
      }

      "Return code 1201 auth command has not specified type" in {
        val probe = TestProbe()
        val shellStdOut = TestProbe()
        val shellStdIn = TestProbe()
        val process = TestProbe()
        val target = system.actorOf(Shell.props(Env(), Vector.empty))

        probe.send(target, CommandsUnion.Commands.Run(process.ref,  Map(0 -> shellStdOut.ref, 1 -> shellStdIn.ref)))
        shellStdOut.expectMsg(Stream.Commands.WriteWrapped(ByteString(Array(PROMPT))))
        shellStdIn.send(target, Stream.Responses.Data(ByteString("auth") ++ ByteString(Array(EOI))))
        shellStdOut.expectMsg(Stream.Commands.WriteWrapped(ByteString("Unknown auth type").eoi.eop.exit(1201)))
      }

      "For command user should return current authorized user name" in {
        val probe = TestProbe()
        val shellStdOut = TestProbe()
        val shellStdIn = TestProbe()
        val process = TestProbe()
        val target = system.actorOf(Shell.props(Env(), Vector.empty))

        probe.send(target, CommandsUnion.Commands.Run(process.ref,  Map(0 -> shellStdOut.ref, 1 -> shellStdIn.ref)))
        shellStdOut.expectMsg(Stream.Commands.WriteWrapped(ByteString(Array(PROMPT))))
        shellStdIn.send(target, Stream.Responses.Data(ByteString("user") ++ ByteString(Array(EOI))))
        shellStdOut.expectMsg(Stream.Commands.WriteWrapped(ByteString("nobody").eoi.eop.exit(0)))
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
        shellStdOut.expectMsg(Stream.Commands.WriteWrapped(ByteString("Wrong statement").eoi.eop.exit(1000)))
        shellStdOut.expectMsg(Stream.Commands.WriteWrapped(ByteString().prompt))
        shellStdIn.send(target, Stream.Responses.Data(ByteString(" ")))
        shellStdIn.send(target, Stream.Responses.Data(ByteString(" ") ++ ByteString(Array(EOI))))
        shellStdOut.expectMsg(Stream.Commands.WriteWrapped(ByteString("Wrong statement").eoi.eop.exit(1000)))
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

    "In authorization mode" should {
      "Return code 0 if authorization was success" in {
        val probe = TestProbe()
        val shellStdOut = TestProbe()
        val shellStdIn = TestProbe()
        val process = TestProbe()
        val simpleAuthenticator = TestProbe()
        val exSystem = new TestActorSystem({
          case Props(_, _, args) if args.head.asInstanceOf[Class[_ <: Actor]].getName == SimpleAuthenticator.getClass.getName.dropRight(1) => simpleAuthenticator.ref
        })
        val target = system.actorOf(Shell.props(Env(), Vector.empty, exSystem))

        probe.send(target, CommandsUnion.Commands.Run(process.ref,  Map(0 -> shellStdOut.ref, 1 -> shellStdIn.ref)))
        shellStdOut.expectMsg(Stream.Commands.WriteWrapped(ByteString(Array(PROMPT))))
        shellStdIn.send(target, Stream.Responses.Data(ByteString("auth simple root 123456") ++ ByteString(Array(EOI))))
        simpleAuthenticator.expectMsg(SimpleAuthenticator.Commands.Exec(Vector("root", "123456")))
        simpleAuthenticator.reply(Shell.Internals.AuthSuccess(UserCredentials("root")))
        shellStdOut.expectMsg(Stream.Commands.WriteWrapped(ByteString("OK").eoi.eop.exit(0)))
      }

      "Return code 1200 if authorization was failed" in {
        val probe = TestProbe()
        val shellStdOut = TestProbe()
        val shellStdIn = TestProbe()
        val process = TestProbe()
        val simpleAuthenticator = TestProbe()
        val exSystem = new TestActorSystem({
          case Props(_, _, args) if args.head.asInstanceOf[Class[_ <: Actor]].getName == SimpleAuthenticator.getClass.getName.dropRight(1) => simpleAuthenticator.ref
        })
        val target = system.actorOf(Shell.props(Env(), Vector.empty, exSystem))

        probe.send(target, CommandsUnion.Commands.Run(process.ref,  Map(0 -> shellStdOut.ref, 1 -> shellStdIn.ref)))
        shellStdOut.expectMsg(Stream.Commands.WriteWrapped(ByteString(Array(PROMPT))))
        shellStdIn.send(target, Stream.Responses.Data(ByteString("auth simple root 123456") ++ ByteString(Array(EOI))))
        simpleAuthenticator.expectMsg(SimpleAuthenticator.Commands.Exec(Vector("root", "123456")))
        simpleAuthenticator.reply(Shell.Internals.AuthFailed("msg"))
        shellStdOut.expectMsg(Stream.Commands.WriteWrapped(ByteString("msg").eoi.eop.exit(1200)))
      }

      "Return code 1202 if authenticator does not respond" in {
        val probe = TestProbe()
        val shellStdOut = TestProbe()
        val shellStdIn = TestProbe()
        val process = TestProbe()
        val simpleAuthenticator = TestProbe()
        val exSystem = new TestActorSystem({
          case Props(_, _, args) if args.head.asInstanceOf[Class[_ <: Actor]].getName == SimpleAuthenticator.getClass.getName.dropRight(1) => simpleAuthenticator.ref
        })
        val target = system.actorOf(Shell.props(Env(), Vector.empty, exSystem, testMode = true))

        probe.send(target, CommandsUnion.Commands.Run(process.ref,  Map(0 -> shellStdOut.ref, 1 -> shellStdIn.ref)))
        shellStdOut.expectMsg(Stream.Commands.WriteWrapped(ByteString(Array(PROMPT))))
        shellStdIn.send(target, Stream.Responses.Data(ByteString("auth simple root 123456") ++ ByteString(Array(EOI))))
        simpleAuthenticator.expectMsg(SimpleAuthenticator.Commands.Exec(Vector("root", "123456")))
        shellStdOut.expectMsg(Stream.Commands.WriteWrapped(ByteString("Authentication timeout").eoi.eop.exit(1202)))
      }

      "Return code 1202 if authenticator respond with AuthTimeout" in {
        val probe = TestProbe()
        val shellStdOut = TestProbe()
        val shellStdIn = TestProbe()
        val process = TestProbe()
        val simpleAuthenticator = TestProbe()
        val exSystem = new TestActorSystem({
          case Props(_, _, args) if args.head.asInstanceOf[Class[_ <: Actor]].getName == SimpleAuthenticator.getClass.getName.dropRight(1) => simpleAuthenticator.ref
        })
        val target = system.actorOf(Shell.props(Env(), Vector.empty, exSystem, testMode = true))

        probe.send(target, CommandsUnion.Commands.Run(process.ref,  Map(0 -> shellStdOut.ref, 1 -> shellStdIn.ref)))
        shellStdOut.expectMsg(Stream.Commands.WriteWrapped(ByteString(Array(PROMPT))))
        shellStdIn.send(target, Stream.Responses.Data(ByteString("auth simple root 123456") ++ ByteString(Array(EOI))))
        simpleAuthenticator.expectMsg(SimpleAuthenticator.Commands.Exec(Vector("root", "123456")))
        simpleAuthenticator.reply(Shell.Internals.AuthTimeout)
        shellStdOut.expectMsg(Stream.Commands.WriteWrapped(ByteString("Authentication timeout").eoi.eop.exit(1202)))
      }

      "Return code 1203 if authenticator respond with InsufficientInputData" in {
        val probe = TestProbe()
        val shellStdOut = TestProbe()
        val shellStdIn = TestProbe()
        val process = TestProbe()
        val simpleAuthenticator = TestProbe()
        val exSystem = new TestActorSystem({
          case Props(_, _, args) if args.head.asInstanceOf[Class[_ <: Actor]].getName == SimpleAuthenticator.getClass.getName.dropRight(1) => simpleAuthenticator.ref
        })
        val target = system.actorOf(Shell.props(Env(), Vector.empty, exSystem, testMode = true))

        probe.send(target, CommandsUnion.Commands.Run(process.ref,  Map(0 -> shellStdOut.ref, 1 -> shellStdIn.ref)))
        shellStdOut.expectMsg(Stream.Commands.WriteWrapped(ByteString(Array(PROMPT))))
        shellStdIn.send(target, Stream.Responses.Data(ByteString("auth simple root 123456") ++ ByteString(Array(EOI))))
        simpleAuthenticator.expectMsg(SimpleAuthenticator.Commands.Exec(Vector("root", "123456")))
        simpleAuthenticator.reply(Shell.Internals.InsufficientInputData)
        shellStdOut.expectMsg(Stream.Commands.WriteWrapped(ByteString("Insufficient input data").eoi.eop.exit(1203)))
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
        pipePreparator.expectMsg(PipePreparator.Commands.Exec(UserCredentials("nobody"), List(CommandNode("a", Vector.empty), CommandNode("b", Vector.empty), CommandNode("c", Vector.empty))))
        pipePreparator.reply(PipePreparator.Responses.CommandsNotFound(Set("a", "c")))
        shellStdOut.expectMsg(Stream.Commands.WriteWrapped(ByteString(s"Commands not found - a, c").eoi.eop.exit(1000)))
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
        pipePreparator.expectMsg(PipePreparator.Commands.Exec(UserCredentials("nobody"), List(CommandNode("a", Vector.empty), CommandNode("b", Vector.empty), CommandNode("c", Vector.empty))))
        pipePreparator.reply(PipePreparator.Responses.InternalError)
        shellStdOut.expectMsg(Stream.Commands.WriteWrapped(ByteString(s"Unable to execute statements due to internal error 2").eoi.eop.exit(1001)))
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
        shellStdOut.expectMsg(Stream.Commands.WriteWrapped(ByteString("Unable to execute statements due to internal error 3").eoi.eop.exit(0)))
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
        pipePreparator.expectMsg(PipePreparator.Commands.Exec(UserCredentials("nobody"), List(CommandNode("a", Vector.empty), CommandNode("b", Vector.empty), CommandNode("c", Vector.empty))))
        pipePreparator.reply(PipePreparator.Responses.PipeCircuit(pipeStdIn.ref, pipeStdOut.ref))
        shellStdOut.expectMsg(Stream.Commands.WriteWrapped(ByteString("IO operations timeout") ++ ByteString(Array(EOI, EOF))))
        shellStdOut.expectMsg(Stream.Commands.WriteWrapped(ByteString(Array(PROMPT))))
      }
    }


  }
}
