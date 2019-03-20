package ru.serbis.okto.node.unit.syscoms.access

import akka.actor.{Actor, ActorSystem, Props}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import akka.util.ByteString
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import ru.serbis.okto.node.common.ReachTypes.ReachByteString
import ru.serbis.okto.node.common.{CommandsUnion, Env}
import ru.serbis.okto.node.log.{StdOutLogger, StreamLogger}
import ru.serbis.okto.node.runtime.Stream
import ru.serbis.okto.node.syscoms.access._
import ru.serbis.okto.node.syscoms.boot._
import ru.serbis.okto.node.testut.TestActorSystem

class AccessSpec extends TestKit(ActorSystem("TestSystem")) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll with StreamLogger {


  override protected def beforeAll(): Unit = {
    initializeGlobalLogger(system)
    logger.addDestination(system.actorOf(StdOutLogger.props))
  }


  "Access" must {
    "Process positive tests" should {
      "For user add options" in {
        val probe = TestProbe()
        val stdOut = TestProbe()
        val stdIn = TestProbe()
        val fsm = TestProbe()
        val process = TestProbe()
        val exSystem = new TestActorSystem({
          case Props(_, _, args) if args.head.asInstanceOf[Class[_ <: Actor]].getName == UserAdd.getClass.getName.dropRight(1) => fsm.ref
        })
        val target = system.actorOf(Access.props(Env(), Vector("user", "add", "b", "c"), exSystem))

        probe.send(target, CommandsUnion.Commands.Run(process.ref,  Map(0 -> stdOut.ref, 1 -> stdIn.ref)))
        fsm.expectMsg(UserAdd.Commands.Exec)
        fsm.reply(Access.Internals.Complete(0, "z"))
        stdOut.expectMsg(Stream.Commands.WriteWrapped(ByteString("z").eoi.eof.exit(0)))
      }

      "For user del options" in {
        val probe = TestProbe()
        val stdOut = TestProbe()
        val stdIn = TestProbe()
        val fsm = TestProbe()
        val process = TestProbe()
        val exSystem = new TestActorSystem({
          case Props(_, _, args) if args.head.asInstanceOf[Class[_ <: Actor]].getName == UserDel.getClass.getName.dropRight(1) => fsm.ref
        })
        val target = system.actorOf(Access.props(Env(), Vector("user", "del", "b", "c"), exSystem))

        probe.send(target, CommandsUnion.Commands.Run(process.ref,  Map(0 -> stdOut.ref, 1 -> stdIn.ref)))
        fsm.expectMsg(UserDel.Commands.Exec)
        fsm.reply(Access.Internals.Complete(0, "z"))
        stdOut.expectMsg(Stream.Commands.WriteWrapped(ByteString("z").eoi.eof.exit(0)))
      }

      "For user info options" in {
        val probe = TestProbe()
        val stdOut = TestProbe()
        val stdIn = TestProbe()
        val fsm = TestProbe()
        val process = TestProbe()
        val exSystem = new TestActorSystem({
          case Props(_, _, args) if args.head.asInstanceOf[Class[_ <: Actor]].getName == UserInfo.getClass.getName.dropRight(1) => fsm.ref
        })
        val target = system.actorOf(Access.props(Env(), Vector("user", "info", "b", "c"), exSystem))

        probe.send(target, CommandsUnion.Commands.Run(process.ref,  Map(0 -> stdOut.ref, 1 -> stdIn.ref)))
        fsm.expectMsg(UserInfo.Commands.Exec)
        fsm.reply(Access.Internals.Complete(0, "z"))
        stdOut.expectMsg(Stream.Commands.WriteWrapped(ByteString("z").eoi.eof.exit(0)))
      }

      "For user list options" in {
        val probe = TestProbe()
        val stdOut = TestProbe()
        val stdIn = TestProbe()
        val fsm = TestProbe()
        val process = TestProbe()
        val exSystem = new TestActorSystem({
          case Props(_, _, args) if args.head.asInstanceOf[Class[_ <: Actor]].getName == UserList.getClass.getName.dropRight(1) => fsm.ref
        })
        val target = system.actorOf(Access.props(Env(), Vector("user", "list", "b", "c"), exSystem))

        probe.send(target, CommandsUnion.Commands.Run(process.ref,  Map(0 -> stdOut.ref, 1 -> stdIn.ref)))
        fsm.expectMsg(UserList.Commands.Exec)
        fsm.reply(Access.Internals.Complete(0, "z"))
        stdOut.expectMsg(Stream.Commands.WriteWrapped(ByteString("z").eoi.eof.exit(0)))
      }

      "For group add options" in {
        val probe = TestProbe()
        val stdOut = TestProbe()
        val stdIn = TestProbe()
        val fsm = TestProbe()
        val process = TestProbe()
        val exSystem = new TestActorSystem({
          case Props(_, _, args) if args.head.asInstanceOf[Class[_ <: Actor]].getName == GroupAdd.getClass.getName.dropRight(1) => fsm.ref
        })
        val target = system.actorOf(Access.props(Env(), Vector("group", "add", "b", "c"), exSystem))

        probe.send(target, CommandsUnion.Commands.Run(process.ref,  Map(0 -> stdOut.ref, 1 -> stdIn.ref)))
        fsm.expectMsg(GroupAdd.Commands.Exec)
        fsm.reply(Access.Internals.Complete(0, "z"))
        stdOut.expectMsg(Stream.Commands.WriteWrapped(ByteString("z").eoi.eof.exit(0)))
      }

      "For group del options" in {
        val probe = TestProbe()
        val stdOut = TestProbe()
        val stdIn = TestProbe()
        val fsm = TestProbe()
        val process = TestProbe()
        val exSystem = new TestActorSystem({
          case Props(_, _, args) if args.head.asInstanceOf[Class[_ <: Actor]].getName == GroupDel.getClass.getName.dropRight(1) => fsm.ref
        })
        val target = system.actorOf(Access.props(Env(), Vector("group", "del", "b", "c"), exSystem))

        probe.send(target, CommandsUnion.Commands.Run(process.ref,  Map(0 -> stdOut.ref, 1 -> stdIn.ref)))
        fsm.expectMsg(GroupDel.Commands.Exec)
        fsm.reply(Access.Internals.Complete(0, "z"))
        stdOut.expectMsg(Stream.Commands.WriteWrapped(ByteString("z").eoi.eof.exit(0)))
      }

      "For group info options" in {
        val probe = TestProbe()
        val stdOut = TestProbe()
        val stdIn = TestProbe()
        val fsm = TestProbe()
        val process = TestProbe()
        val exSystem = new TestActorSystem({
          case Props(_, _, args) if args.head.asInstanceOf[Class[_ <: Actor]].getName == GroupInfo.getClass.getName.dropRight(1) => fsm.ref
        })
        val target = system.actorOf(Access.props(Env(), Vector("group", "info", "b", "c"), exSystem))

        probe.send(target, CommandsUnion.Commands.Run(process.ref,  Map(0 -> stdOut.ref, 1 -> stdIn.ref)))
        fsm.expectMsg(GroupInfo.Commands.Exec)
        fsm.reply(Access.Internals.Complete(0, "z"))
        stdOut.expectMsg(Stream.Commands.WriteWrapped(ByteString("z").eoi.eof.exit(0)))
      }

      "For group list options" in {
        val probe = TestProbe()
        val stdOut = TestProbe()
        val stdIn = TestProbe()
        val fsm = TestProbe()
        val process = TestProbe()
        val exSystem = new TestActorSystem({
          case Props(_, _, args) if args.head.asInstanceOf[Class[_ <: Actor]].getName == GroupList.getClass.getName.dropRight(1) => fsm.ref
        })
        val target = system.actorOf(Access.props(Env(), Vector("group", "list", "b", "c"), exSystem))

        probe.send(target, CommandsUnion.Commands.Run(process.ref,  Map(0 -> stdOut.ref, 1 -> stdIn.ref)))
        fsm.expectMsg(GroupList.Commands.Exec)
        fsm.reply(Access.Internals.Complete(0, "z"))
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
        val target = system.actorOf(Access.props(Env(), Vector("--wrong", "x"), exSystem))

        probe.send(target, CommandsUnion.Commands.Run(process.ref,  Map(0 -> stdOut.ref, 1 -> stdIn.ref)))
        stdOut.expectMsg(Stream.Commands.WriteWrapped(ByteString("Unexpected option '--wrong'").eoi.eof.exit(1)))
      }
    }

    "Return not enough arguments if first two arg is not presented" in {
      val probe = TestProbe()
      val stdOut = TestProbe()
      val stdIn = TestProbe()
      val pmList = TestProbe()
      val process = TestProbe()
      val exSystem = new TestActorSystem({
        case Props(_, _, args) if args.head.asInstanceOf[Class[_ <: Actor]].getName == BootInfo.getClass.getName.dropRight(1) => pmList.ref
      })
      val target = system.actorOf(Access.props(Env(), Vector.empty, exSystem))

      probe.send(target, CommandsUnion.Commands.Run(process.ref,  Map(0 -> stdOut.ref, 1 -> stdIn.ref)))
      stdOut.expectMsg(Stream.Commands.WriteWrapped(ByteString("Not enough arguments").eoi.eof.exit(1)))
    }
  }
}
