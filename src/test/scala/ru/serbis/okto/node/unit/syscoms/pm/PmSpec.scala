package ru.serbis.okto.node.unit.syscoms.pm

import akka.actor.{Actor, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import akka.util.ByteString
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import ru.serbis.okto.node.common.{CommandsUnion, Env}
import ru.serbis.okto.node.log.{StdOutLogger, StreamLogger}
import ru.serbis.okto.node.runtime.Stream
import ru.serbis.okto.node.runtime.StreamControls._
import ru.serbis.okto.node.syscoms.pm._
import ru.serbis.okto.node.syscoms.shell.StatementsParser.CommandNode
import ru.serbis.okto.node.syscoms.shell.{PipePreparator, Shell}
import ru.serbis.okto.node.testut.TestActorSystem
import ru.serbis.okto.node.common.ReachTypes.ReachByteString

class PmSpec extends TestKit(ActorSystem("TestSystem")) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll with StreamLogger {


  override protected def beforeAll(): Unit = {
    initializeGlobalLogger(system)
    logger.addDestination(system.actorOf(StdOutLogger.props))
  }


  "Pm" must {
    "Process positive tests" should {
      "For for --install option" in {
        val probe = TestProbe()
        val stdOut = TestProbe()
        val stdIn = TestProbe()
        val pmInstall = TestProbe()
        val process = TestProbe()
            val exSystem = new TestActorSystem({
          case Props(_, _, args) if args.head.asInstanceOf[Class[_ <: Actor]].getName == PmInstall.getClass.getName.dropRight(1) => pmInstall.ref
        })
        val target = system.actorOf(Pm.props(Env(), Vector("--install", "x"), exSystem))

        probe.send(target, CommandsUnion.Commands.Run(process.ref,  Map(0 -> stdOut.ref, 1 -> stdIn.ref)))
        stdIn.expectMsg(Stream.Commands.Detach(target))
        stdIn.expectMsg(Stream.Commands.Attach(pmInstall.ref))
        pmInstall.expectMsg(PmInstall.Commands.Exec)
        pmInstall.reply(Pm.Internals.Complete(0, "z"))
        stdOut.expectMsg(Stream.Commands.WriteWrapped(ByteString("z").eoi.eof.exit(0)))
      }

      "For --remove option" in {
        val probe = TestProbe()
        val stdOut = TestProbe()
        val stdIn = TestProbe()
        val pmRemove = TestProbe()
        val process = TestProbe()
        val exSystem = new TestActorSystem({
          case Props(_, _, args) if args.head.asInstanceOf[Class[_ <: Actor]].getName == PmRemove.getClass.getName.dropRight(1) => pmRemove.ref
        })
        val target = system.actorOf(Pm.props(Env(), Vector("--remove", "x"), exSystem))

        probe.send(target, CommandsUnion.Commands.Run(process.ref,  Map(0 -> stdOut.ref, 1 -> stdIn.ref)))
        pmRemove.expectMsg(PmRemove.Commands.Exec)
        pmRemove.reply(Pm.Internals.Complete(0, "z"))
        stdOut.expectMsg(Stream.Commands.WriteWrapped(ByteString("z").eoi.eof.exit(0)))
      }

      "For --list option" in {
        val probe = TestProbe()
        val stdOut = TestProbe()
        val stdIn = TestProbe()
        val pmList = TestProbe()
        val process = TestProbe()
        val exSystem = new TestActorSystem({
          case Props(_, _, args) if args.head.asInstanceOf[Class[_ <: Actor]].getName == PmList.getClass.getName.dropRight(1) => pmList.ref
        })
        val target = system.actorOf(Pm.props(Env(), Vector("--list", "x"), exSystem))

        probe.send(target, CommandsUnion.Commands.Run(process.ref,  Map(0 -> stdOut.ref, 1 -> stdIn.ref)))
        pmList.expectMsg(PmList.Commands.Exec)
        pmList.reply(Pm.Internals.Complete(0, "z"))
        stdOut.expectMsg(Stream.Commands.WriteWrapped(ByteString("z").eoi.eof.exit(0)))
      }

      "For --code option" in {
        val probe = TestProbe()
        val stdOut = TestProbe()
        val stdIn = TestProbe()
        val pmCode = TestProbe()
        val process = TestProbe()
        val exSystem = new TestActorSystem({
          case Props(_, _, args) if args.head.asInstanceOf[Class[_ <: Actor]].getName == PmCode.getClass.getName.dropRight(1) => pmCode.ref
        })
        val target = system.actorOf(Pm.props(Env(), Vector("--code", "x"), exSystem))

        probe.send(target, CommandsUnion.Commands.Run(process.ref,  Map(0 -> stdOut.ref, 1 -> stdIn.ref)))
        pmCode.expectMsg(PmCode.Commands.Exec)
        pmCode.reply(Pm.Internals.Complete(0, "z"))
        stdOut.expectMsg(Stream.Commands.WriteWrapped(ByteString("z").eoi.eof.exit(0)))
      }

      "For some wrong option" in {
        val probe = TestProbe()
        val stdOut = TestProbe()
        val stdIn = TestProbe()
        val pmList = TestProbe()
        val process = TestProbe()
        val exSystem = new TestActorSystem({
          case Props(_, _, args) if args.head.asInstanceOf[Class[_ <: Actor]].getName == PmList.getClass.getName.dropRight(1) => pmList.ref
        })
        val target = system.actorOf(Pm.props(Env(), Vector("--wrong", "x"), exSystem))

        probe.send(target, CommandsUnion.Commands.Run(process.ref,  Map(0 -> stdOut.ref, 1 -> stdIn.ref)))
        stdOut.expectMsg(Stream.Commands.WriteWrapped(ByteString("Unexpected option '--wrong'").eoi.eof.exit(1)))
      }
    }

    "Return not enough arguments if first arg is not presented" in {
      val probe = TestProbe()
      val stdOut = TestProbe()
      val stdIn = TestProbe()
      val pmList = TestProbe()
      val process = TestProbe()
      val exSystem = new TestActorSystem({
        case Props(_, _, args) if args.head.asInstanceOf[Class[_ <: Actor]].getName == PmList.getClass.getName.dropRight(1) => pmList.ref
      })
      val target = system.actorOf(Pm.props(Env(), Vector.empty, exSystem))

      probe.send(target, CommandsUnion.Commands.Run(process.ref,  Map(0 -> stdOut.ref, 1 -> stdIn.ref)))
      stdOut.expectMsg(Stream.Commands.WriteWrapped(ByteString("Not enough arguments").eoi.eof.exit(2)))
    }
  }
}
