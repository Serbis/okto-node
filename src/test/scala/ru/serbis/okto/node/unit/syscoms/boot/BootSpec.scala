package ru.serbis.okto.node.unit.syscoms.boot

import akka.actor.{Actor, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import akka.util.ByteString
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import ru.serbis.okto.node.common.ReachTypes.ReachByteString
import ru.serbis.okto.node.common.{CommandsUnion, Env}
import ru.serbis.okto.node.log.{StdOutLogger, StreamLogger}
import ru.serbis.okto.node.runtime.Stream
import ru.serbis.okto.node.syscoms.boot._
import ru.serbis.okto.node.testut.TestActorSystem

class BootSpec extends TestKit(ActorSystem("TestSystem")) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll with StreamLogger {


  override protected def beforeAll(): Unit = {
    initializeGlobalLogger(system)
    logger.addDestination(system.actorOf(StdOutLogger.props))
  }


  "Boot" must {
    "Process positive tests" should {
      "For --info option" in {
        val probe = TestProbe()
        val stdOut = TestProbe()
        val stdIn = TestProbe()
        val fsm = TestProbe()
        val process = TestProbe()
        val exSystem = new TestActorSystem({
          case Props(_, _, args) if args.head.asInstanceOf[Class[_ <: Actor]].getName == BootInfo.getClass.getName.dropRight(1) => fsm.ref
        })
        val target = system.actorOf(Boot.props(Env(), Vector("--info", "a", "b", "c"), exSystem))

        probe.send(target, CommandsUnion.Commands.Run(process.ref,  Map(0 -> stdOut.ref, 1 -> stdIn.ref)))
        fsm.expectMsg(BootInfo.Commands.Exec)
        fsm.reply(Boot.Internals.Complete(0, "z"))
        stdOut.expectMsg(Stream.Commands.WriteWrapped(ByteString("z").eoi.eof.exit(0)))
      }

      "For --add option" in {
        val probe = TestProbe()
        val stdOut = TestProbe()
        val stdIn = TestProbe()
        val fsm = TestProbe()
        val process = TestProbe()
        val exSystem = new TestActorSystem({
          case Props(_, _, args) if args.head.asInstanceOf[Class[_ <: Actor]].getName == BootAdd.getClass.getName.dropRight(1) => fsm.ref
        })
        val target = system.actorOf(Boot.props(Env(), Vector("--add", "a", "b", "c"), exSystem))

        probe.send(target, CommandsUnion.Commands.Run(process.ref,  Map(0 -> stdOut.ref, 1 -> stdIn.ref)))
        fsm.expectMsg(BootAdd.Commands.Exec)
        fsm.reply(Boot.Internals.Complete(0, "z"))
        stdOut.expectMsg(Stream.Commands.WriteWrapped(ByteString("z").eoi.eof.exit(0)))
      }

      "For --remove option" in {
        val probe = TestProbe()
        val stdOut = TestProbe()
        val stdIn = TestProbe()
        val fsm = TestProbe()
        val process = TestProbe()
        val exSystem = new TestActorSystem({
          case Props(_, _, args) if args.head.asInstanceOf[Class[_ <: Actor]].getName == BootRemove.getClass.getName.dropRight(1) => fsm.ref
        })
        val target = system.actorOf(Boot.props(Env(), Vector("--remove", "a", "b", "c"), exSystem))

        probe.send(target, CommandsUnion.Commands.Run(process.ref,  Map(0 -> stdOut.ref, 1 -> stdIn.ref)))
        fsm.expectMsg(BootRemove.Commands.Exec)
        fsm.reply(Boot.Internals.Complete(0, "z"))
        stdOut.expectMsg(Stream.Commands.WriteWrapped(ByteString("z").eoi.eof.exit(0)))
      }

      "For some wrong option" in {
        val probe = TestProbe()
        val stdOut = TestProbe()
        val stdIn = TestProbe()
        val pmList = TestProbe()
        val process = TestProbe()
        val exSystem = new TestActorSystem({
          case Props(_, _, args) if args.head.asInstanceOf[Class[_ <: Actor]].getName == BootInfo.getClass.getName.dropRight(1) => pmList.ref
        })
        val target = system.actorOf(Boot.props(Env(), Vector("--wrong", "x"), exSystem))

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
        case Props(_, _, args) if args.head.asInstanceOf[Class[_ <: Actor]].getName == BootInfo.getClass.getName.dropRight(1) => pmList.ref
      })
      val target = system.actorOf(Boot.props(Env(), Vector.empty, exSystem))

      probe.send(target, CommandsUnion.Commands.Run(process.ref,  Map(0 -> stdOut.ref, 1 -> stdIn.ref)))
      stdOut.expectMsg(Stream.Commands.WriteWrapped(ByteString("Not enough arguments").eoi.eof.exit(1)))
    }
  }
}
