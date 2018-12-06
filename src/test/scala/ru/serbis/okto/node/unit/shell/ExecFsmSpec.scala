package ru.serbis.okto.node.unit.shell

import java.io.File

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import ru.serbis.okto.node.log.{StdOutLogger, StreamLogger}

class ExecFsmSpec extends TestKit(ActorSystem("TestSystem")) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll with StreamLogger {

  val commandsDat = new File("fect/commands.dat")

  override protected def beforeAll(): Unit = {
    initializeGlobalLogger(system)
    logger.addDestination(system.actorOf(StdOutLogger.props))
  }

  /*"ExecFsm" must {
    "Involve Runtime to spawn new program and return it pid in system command exists scenario" in {
      val probe = TestProbe()
      val runtime = TestProbe()
      val systemCommandsRep = TestProbe()
      val userCommandsRep = TestProbe()
      val target = system.actorOf(ExecFsm.props(Conf(systemCommandsRep = systemCommandsRep.ref, userCommandsRep = userCommandsRep.ref), runtime.ref))
      probe.send(target, ExecFsm.Commands.Exec("""cmd arg1 -arg2 --arg3 'arg4 with space' 'arg5 with \' screen'"""))
      systemCommandsRep.expectMsg(SyscomsRep.Commands.CheckCmdExist("cmd"))
      systemCommandsRep.reply(SyscomsRep.Responses.Exist)
      runtime.expectMsg(Runtime.Commands.Spawn("cmd", Vector("arg1", "-arg2", "--arg3", "arg4 with space", "arg5 with ' screen"), system = true, probe.ref))
      runtime.reply(Runtime.Responses.Pid(200))
      probe.expectMsg(proto_messages.SpawnedProcessPid(200))
    }

    "Involve Runtime to spawn new program and return it pid in system command not exists scenario" in {
      val probe = TestProbe()
      val runtime = TestProbe()
      val systemCommandsRep = TestProbe()
      val userCommandsRep = TestProbe()
      val target = system.actorOf(ExecFsm.props(Conf(systemCommandsRep = systemCommandsRep.ref, userCommandsRep = userCommandsRep.ref), runtime.ref))
      probe.send(target, ExecFsm.Commands.Exec(""" cmd arg1 -arg2 --arg3 'arg4 with space' 'arg5 with \' screen'""")) //Побел в начале добавлен специально
      systemCommandsRep.expectMsg(SyscomsRep.Commands.CheckCmdExist("cmd"))
      systemCommandsRep.reply(SyscomsRep.Responses.NotExist)
      userCommandsRep.expectMsg(UserCommandsRep.Commands.CheckCmdExist("cmd"))
      userCommandsRep.reply(UserCommandsRep.Responses.Exist)
      runtime.expectMsg(Runtime.Commands.Spawn("cmd", Vector("arg1", "-arg2", "--arg3", "arg4 with space", "arg5 with ' screen"), system = true, probe.ref))
      runtime.reply(Runtime.Responses.Pid(200))
      probe.expectMsg(proto_messages.SpawnedProcessPid(200))
    }

    "Return CommandNotFound if command not exist anywhere" in {
      val probe = TestProbe()
      val runtime = TestProbe()
      val systemCommandsRep = TestProbe()
      val userCommandsRep = TestProbe()
      val target = system.actorOf(ExecFsm.props(Conf(systemCommandsRep = systemCommandsRep.ref, userCommandsRep = userCommandsRep.ref), runtime.ref))
      probe.send(target, ExecFsm.Commands.Exec("""cmd arg1 -arg2 --arg3 'arg4 with space' 'arg5 with \' screen'"""))
      systemCommandsRep.expectMsg(SyscomsRep.Commands.CheckCmdExist("cmd"))
      systemCommandsRep.reply(SyscomsRep.Responses.NotExist)
      userCommandsRep.expectMsg(UserCommandsRep.Commands.CheckCmdExist("cmd"))
      userCommandsRep.reply(UserCommandsRep.Responses.NotExist)
      probe.expectMsg(proto_messages.CommandNotFound())
    }


    "Return InternalError if SystemCommandsRep does not respond with expected timeout" in {
      val probe = TestProbe()
      val runtime = TestProbe()
      val systemCommandsRep = TestProbe()
      val userCommandsRep = TestProbe()
      val target = system.actorOf(ExecFsm.props(Conf(systemCommandsRep = systemCommandsRep.ref, userCommandsRep = userCommandsRep.ref), runtime.ref, testMode = true))
      probe.send(target, ExecFsm.Commands.Exec("""cmd arg1 -arg2 --arg3 'arg4 with space' 'arg5 with \' screen'"""))
      probe.expectMsg(proto_messages.InternalError())
    }

    "Return InternalError if UserCommandsRep does not respond with expected timeout" in {
      val probe = TestProbe()
      val runtime = TestProbe()
      val systemCommandsRep = TestProbe()
      val userCommandsRep = TestProbe()
      val target = system.actorOf(ExecFsm.props(Conf(systemCommandsRep = systemCommandsRep.ref, userCommandsRep = userCommandsRep.ref), runtime.ref, testMode = true))
      probe.send(target, ExecFsm.Commands.Exec("""cmd arg1 -arg2 --arg3 'arg4 with space' 'arg5 with \' screen'"""))
      systemCommandsRep.expectMsg(SyscomsRep.Commands.CheckCmdExist("cmd"))
      systemCommandsRep.reply(SyscomsRep.Responses.NotExist)
      probe.expectMsg(proto_messages.InternalError())
    }

    "Return InternalError if Runtime does not respond with expected timeout" in {
      val probe = TestProbe()
      val runtime = TestProbe()
      val systemCommandsRep = TestProbe()
      val userCommandsRep = TestProbe()
      val target = system.actorOf(ExecFsm.props(Conf(systemCommandsRep = systemCommandsRep.ref, userCommandsRep = userCommandsRep.ref), runtime.ref, testMode = true))
      probe.send(target, ExecFsm.Commands.Exec("""cmd arg1 -arg2 --arg3 'arg4 with space' 'arg5 with \' screen'"""))
      systemCommandsRep.expectMsg(SyscomsRep.Commands.CheckCmdExist("cmd"))
      systemCommandsRep.reply(SyscomsRep.Responses.NotExist)
      userCommandsRep.expectMsg(UserCommandsRep.Commands.CheckCmdExist("cmd"))
      userCommandsRep.reply(UserCommandsRep.Responses.Exist)
      probe.expectMsg(proto_messages.InternalError())
    }

    "Return EmptyCommandString if passed command is empty" in {
      val probe = TestProbe()
      val runtime = TestProbe()
      val systemCommandsRep = TestProbe()
      val userCommandsRep = TestProbe()
      val target = system.actorOf(ExecFsm.props(Conf(systemCommandsRep = systemCommandsRep.ref, userCommandsRep = userCommandsRep.ref), runtime.ref, testMode = true))
      probe.send(target, ExecFsm.Commands.Exec(""" """))
      probe.expectMsg(proto_messages.EmptyCommandLine())
    }

    "Return runtime error code if runtime return some error" in {
      //Этот тест оставлен на будущее, когда runtime бдует возваращать какие-то ошибки при сауне процессов
    }
  }*/

}
