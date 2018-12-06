package ru.serbis.okto.node.unit.shell

import java.io.File

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import ru.serbis.okto.node.log.{StdOutLogger, StreamLogger}


class ShellSpec extends TestKit(ActorSystem("TestSystem")) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll with StreamLogger {

  val commandsDat = new File("fect/commands.dat")

  override protected def beforeAll(): Unit = {
    initializeGlobalLogger(system)
    logger.addDestination(system.actorOf(StdOutLogger.props))
  }

  /*"Shell" must {
    "After receive ExecCmd command must start ExecFsm with correct parameters" in { //This test check only fsm starting up fact
      val probe = TestProbe()
      val runtime = TestProbe()
      val systemCommandsRep = TestProbe()
      val userCommandsRep = TestProbe()
      val shellConfig = ShellConfiguration(
        conf = Conf(
          systemCommandsRep = systemCommandsRep.ref,
          userCommandsRep = userCommandsRep.ref
        ),
        runtime = runtime.ref
      )
      val target = system.actorOf(Shell.props(shellConfig))
      target ! proto_messages.ExecCmd("cmd")
      systemCommandsRep.expectMsg(SyscomsRep.Commands.CheckCmdExist("cmd"))
    }
  }*/

}
