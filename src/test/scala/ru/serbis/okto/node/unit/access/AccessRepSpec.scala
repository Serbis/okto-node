package ru.serbis.okto.node.unit.access

import java.io.{File, IOException}
import java.nio.file.StandardOpenOption

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import akka.util.ByteString
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike, path}
import ru.serbis.okto.node.access.AccessCredentials.{GroupCredentials, Permissions, UserCredentials}
import ru.serbis.okto.node.access.{AccessCredentials, AccessRep}
import ru.serbis.okto.node.boot.BootManager
import ru.serbis.okto.node.common.ReachTypes.ReachByteString
import ru.serbis.okto.node.common.{CommandsUnion, Env}
import ru.serbis.okto.node.log.{StdOutLogger, StreamLogger}
import ru.serbis.okto.node.proxy.files.TestFilesProxy
import ru.serbis.okto.node.proxy.system.TestActorSystemProxy
import ru.serbis.okto.node.reps.BootRep
import ru.serbis.okto.node.runtime.Stream
import ru.serbis.okto.node.syscoms.shell.Shell


class AccessRepSpec extends TestKit(ActorSystem("TestSystem")) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll with StreamLogger {

  val cfgFull = "users: [\n  {\n    name: \"nobody\"\n    password: \"aaa\"\n    salt: \"xxx\"\n    permissions: []\n    groups: []\n  },\n  {\n    name: \"root\"\n    password: \"bbb\"\n    salt: \"yyy\"\n    permissions: [\"All\"]\n    groups: [\"root\"]\n  },\n  {\n    name: \"foo\"\n    password: \"ccc\"\n    salt: \"zzz\"\n    permissions: [\"RunScripts\"]\n    groups: [\"root\"]\n  }\n]\n\ngroups: [\n  {\n    name: \"root\",\n    permissions: [\"All\"]\n  },\n  {\n    name: \"standard\",\n    permissions: [\"RunSystemCommands\"]\n  }\n]"
  override protected def beforeAll(): Unit = {
    initializeGlobalLogger(system)
    logger.addDestination(system.actorOf(StdOutLogger.props))
  }

  "AccessRep" must {
    "After start load configuration from the disk" in {
      val filesProxy = TestProbe()
      system.actorOf(AccessRep.props("/foo", new TestFilesProxy(filesProxy.ref)))
      filesProxy.expectMsg(TestFilesProxy.Actions.ReadAllBytes(new File("/foo").toPath))
      filesProxy.reply(ByteString(cfgFull).toArray)
    }

    "For AddGroup command" should {
      "Return Success after success write new config to the disk" in {
        val (target, probe, filesProxy) = prepareKit(cfgFull)
        probe.send(target, AccessRep.Commands.AddGroup("bar", Vector("RunScripts", "RunSystemCommands")))
        val tf = new File("/foo").toPath
        val msg = filesProxy.expectMsgType[TestFilesProxy.Actions.Write]
        msg.path shouldEqual tf
        msg.bytes.utf8String shouldEqual "users: [\n  {\n    name: \"nobody\"\n    password: \"aaa\"\n    salt: \"xxx\"\n    permissions: []\n    groups: []\n  },\n  {\n    name: \"root\"\n    password: \"bbb\"\n    salt: \"yyy\"\n    permissions: [\"All\"]\n    groups: [\"root\"]\n  },\n  {\n    name: \"foo\"\n    password: \"ccc\"\n    salt: \"zzz\"\n    permissions: [\"RunScripts\"]\n    groups: [\"root\"]\n  }\n]\n\ngroups: [\n  {\n    name: \"bar\",\n    permissions: [\"RunScripts\", \"RunSystemCommands\"]\n  },\n  {\n    name: \"root\",\n    permissions: [\"All\"]\n  },\n  {\n    name: \"standard\",\n    permissions: [\"RunSystemCommands\"]\n  }\n]"
        filesProxy.reply(tf)
        probe.expectMsg(AccessRep.Responses.Success)
      }

      "Return UnknownPermission if bad permission string was passed" in {
        val (target, probe, _) = prepareKit(cfgFull)
        probe.send(target, AccessRep.Commands.AddGroup("bar", Vector("zzz", "RunSystemCommands")))
        probe.expectMsg(AccessRep.Responses.UnknownPermission("zzz"))
      }

      "Return Exist if group already exist" in {
        val (target, probe, _) = prepareKit(cfgFull)
        probe.send(target, AccessRep.Commands.AddGroup("root", Vector("RunScripts", "RunSystemCommands")))
        probe.expectMsg(AccessRep.Responses.Exist)
      }


      "Return WriteError if some io error was occurs" in {
        val (target, probe, filesProxy) = prepareKit(cfgFull)
        probe.send(target, AccessRep.Commands.AddGroup("bar", Vector("RunScripts", "RunSystemCommands")))
        val tf = new File("/foo").toPath
        val msg = filesProxy.expectMsgType[TestFilesProxy.Actions.Write]
        val ex = new IOException("xxx")
        filesProxy.reply(TestFilesProxy.Predicts.Throw(ex))
        probe.expectMsg(AccessRep.Responses.WriteError(ex))
      }
    }

    "For AddUser command" should {
      "Return Success after success write new config to the disk" in {
        val (target, probe, filesProxy) = prepareKit(cfgFull)
        probe.send(target, AccessRep.Commands.AddUser("bar", "xxx", Vector("RunScripts", "RunSystemCommands"), Vector("root", "standard")))
        val tf = new File("/foo").toPath
        val msg = filesProxy.expectMsgType[TestFilesProxy.Actions.Write]
        msg.path shouldEqual tf
        val expectedPwd = AccessCredentials.hashPassword("xxx", "xxx")
        msg.bytes.utf8String shouldEqual "users: [\n  {\n    name: \"bar\"\n    password: \"" + expectedPwd + "\"\n    salt: \"xxx\"\n    permissions: [\"RunScripts\", \"RunSystemCommands\"]\n    groups: [\"root\", \"standard\"]\n  },\n  {\n    name: \"nobody\"\n    password: \"aaa\"\n    salt: \"xxx\"\n    permissions: []\n    groups: []\n  },\n  {\n    name: \"root\"\n    password: \"bbb\"\n    salt: \"yyy\"\n    permissions: [\"All\"]\n    groups: [\"root\"]\n  },\n  {\n    name: \"foo\"\n    password: \"ccc\"\n    salt: \"zzz\"\n    permissions: [\"RunScripts\"]\n    groups: [\"root\"]\n  }\n]\n\ngroups: [\n  {\n    name: \"root\",\n    permissions: [\"All\"]\n  },\n  {\n    name: \"standard\",\n    permissions: [\"RunSystemCommands\"]\n  }\n]"
        filesProxy.reply(tf)
        probe.expectMsg(AccessRep.Responses.Success)
      }

      "Return UnknownPermission if bad permission string was passed" in {
        val (target, probe, _) = prepareKit(cfgFull)
        probe.send(target, AccessRep.Commands.AddUser("root", "xxx", Vector("RunScripts", "xxx"), Vector("root", "standard")))
        probe.expectMsg(AccessRep.Responses.UnknownPermission("xxx"))
      }

      "Return Exist if user already exist" in {
        val (target, probe, _) = prepareKit(cfgFull)
        probe.send(target, AccessRep.Commands.AddUser("root", "xxx", Vector("RunScripts", "RunSystemCommands"), Vector("root", "standard")))
        probe.expectMsg(AccessRep.Responses.Exist)
      }

      "Return GroupNotExist, if the user consist in the not exist group" in {
        val (target, probe, _) = prepareKit(cfgFull)
        probe.send(target, AccessRep.Commands.AddUser("root", "xxx", Vector("RunScripts", "RunSystemCommands"), Vector("root", "xxx")))
        probe.expectMsg(AccessRep.Responses.GroupNotExist("xxx"))
      }


      "Return WriteError if some io error was occurs" in {
        val (target, probe, filesProxy) = prepareKit(cfgFull)
        probe.send(target, AccessRep.Commands.AddUser("bar", "xxx", Vector("RunScripts", "RunSystemCommands"), Vector("root", "standard")))
        val tf = new File("/foo").toPath
        val msg = filesProxy.expectMsgType[TestFilesProxy.Actions.Write]
        val ex = new IOException("xxx")
        filesProxy.reply(TestFilesProxy.Predicts.Throw(ex))
        probe.expectMsg(AccessRep.Responses.WriteError(ex))
      }
    }

    "For DelGroup command" should {
      "Return Success after success write new config to the disk" in {
        val (target, probe, filesProxy) = prepareKit(cfgFull)
        probe.send(target, AccessRep.Commands.DelGroup("root"))
        val tf = new File("/foo").toPath
        val msg = filesProxy.expectMsgType[TestFilesProxy.Actions.Write]
        msg.path shouldEqual tf
        msg.bytes.utf8String shouldEqual "users: [\n  {\n    name: \"nobody\"\n    password: \"aaa\"\n    salt: \"xxx\"\n    permissions: []\n    groups: []\n  },\n  {\n    name: \"root\"\n    password: \"bbb\"\n    salt: \"yyy\"\n    permissions: [\"All\"]\n    groups: []\n  },\n  {\n    name: \"foo\"\n    password: \"ccc\"\n    salt: \"zzz\"\n    permissions: [\"RunScripts\"]\n    groups: []\n  }\n]\n\ngroups: [\n  {\n    name: \"standard\",\n    permissions: [\"RunSystemCommands\"]\n  }\n]"
        filesProxy.reply(tf)
        probe.expectMsg(AccessRep.Responses.Success)
      }

      "Return Success after success write new config to the disk and don't remove delete group from users" in {
        val (target, probe, filesProxy) = prepareKit(cfgFull)
        probe.send(target, AccessRep.Commands.DelGroup("root", recursive = false))
        val tf = new File("/foo").toPath
        val msg = filesProxy.expectMsgType[TestFilesProxy.Actions.Write]
        msg.path shouldEqual tf
        msg.bytes.utf8String shouldEqual "users: [\n  {\n    name: \"nobody\"\n    password: \"aaa\"\n    salt: \"xxx\"\n    permissions: []\n    groups: []\n  },\n  {\n    name: \"root\"\n    password: \"bbb\"\n    salt: \"yyy\"\n    permissions: [\"All\"]\n    groups: [\"root\"]\n  },\n  {\n    name: \"foo\"\n    password: \"ccc\"\n    salt: \"zzz\"\n    permissions: [\"RunScripts\"]\n    groups: [\"root\"]\n  }\n]\n\ngroups: [\n  {\n    name: \"standard\",\n    permissions: [\"RunSystemCommands\"]\n  }\n]"
        filesProxy.reply(tf)
        probe.expectMsg(AccessRep.Responses.Success)
      }

      "Return NotExist, if deleted group does not exist" in {
        val (target, probe, _) = prepareKit(cfgFull)
        probe.send(target, AccessRep.Commands.DelGroup("asd"))
        probe.expectMsg(AccessRep.Responses.NotExist)
      }

      "Return WriteError if some io error was occurs" in {
        val (target, probe, filesProxy) = prepareKit(cfgFull)
        probe.send(target, AccessRep.Commands.DelGroup("root"))
        val tf = new File("/foo").toPath
        val msg = filesProxy.expectMsgType[TestFilesProxy.Actions.Write]
        val ex = new IOException("xxx")
        filesProxy.reply(TestFilesProxy.Predicts.Throw(ex))
        probe.expectMsg(AccessRep.Responses.WriteError(ex))
      }
    }

    "For DelUser command" should {
      "Return Success after success write new config to the disk" in {
        val (target, probe, filesProxy) = prepareKit(cfgFull)
        probe.send(target, AccessRep.Commands.DelUser("root"))
        val tf = new File("/foo").toPath
        val msg = filesProxy.expectMsgType[TestFilesProxy.Actions.Write]
        msg.path shouldEqual tf
        msg.bytes.utf8String shouldEqual "users: [\n  {\n    name: \"nobody\"\n    password: \"aaa\"\n    salt: \"xxx\"\n    permissions: []\n    groups: []\n  },\n  {\n    name: \"foo\"\n    password: \"ccc\"\n    salt: \"zzz\"\n    permissions: [\"RunScripts\"]\n    groups: [\"root\"]\n  }\n]\n\ngroups: [\n  {\n    name: \"root\",\n    permissions: [\"All\"]\n  },\n  {\n    name: \"standard\",\n    permissions: [\"RunSystemCommands\"]\n  }\n]"
        filesProxy.reply(tf)
        probe.expectMsg(AccessRep.Responses.Success)
      }

      "Return NotExist, if deleted user does not exist" in {
        val (target, probe, _) = prepareKit(cfgFull)
        probe.send(target, AccessRep.Commands.DelUser("asd"))
        probe.expectMsg(AccessRep.Responses.NotExist)
      }

      "Return WriteError if some io error was occurs" in {
        val (target, probe, filesProxy) = prepareKit(cfgFull)
        probe.send(target, AccessRep.Commands.DelUser("root"))
        val tf = new File("/foo").toPath
        val msg = filesProxy.expectMsgType[TestFilesProxy.Actions.Write]
        val ex = new IOException("xxx")
        filesProxy.reply(TestFilesProxy.Predicts.Throw(ex))
        probe.expectMsg(AccessRep.Responses.WriteError(ex))
      }
    }

    "For GetAccessCredentials command must return all existed credentials" in {
      val (target, probe, _) = prepareKit(cfgFull)
      probe.send(target, AccessRep.Commands.GetAccessConfig)
      probe.expectMsg(AccessRep.Definitions.AccessConfig(
        Vector(
          AccessRep.Definitions.UserDefinition("nobody", "aaa", "xxx", Vector.empty, Vector.empty),
          AccessRep.Definitions.UserDefinition("root", "bbb", "yyy", Vector("All"), Vector("root")),
          AccessRep.Definitions.UserDefinition("foo", "ccc", "zzz", Vector("RunScripts"), Vector("root"))
        ),
        Vector(
          AccessRep.Definitions.GroupDefinition("root", Vector("All")),
          AccessRep.Definitions.GroupDefinition("standard", Vector("RunSystemCommands"))
        )
      ))
    }

    "For GetPermissionsDefinition command" must {
      "Return AccessCredentials" in {
        val (target, probe, _) = prepareKit(cfgFull)
        probe.send(target, AccessRep.Commands.GetPermissionsDefinition("foo"))
        probe.expectMsg(AccessCredentials.UserCredentials("foo", "ccc", "zzz", Set(Permissions.RunScripts), Set(AccessCredentials.GroupCredentials("root", Set(Permissions.All)))))
      }

      "Return NotExist if user does not exist" in {
        val (target, probe, _) = prepareKit(cfgFull)
        probe.send(target, AccessRep.Commands.GetPermissionsDefinition("xxx"))
        probe.expectMsg(AccessRep.Responses.NotExist)
      }
    }
  }

  def prepareKit(cfg: String) = {
    val probe = TestProbe()
    val filesProxy = TestProbe()
    val target = system.actorOf(AccessRep.props("/foo", new TestFilesProxy(filesProxy.ref), tm = true))
    filesProxy.expectMsg(TestFilesProxy.Actions.ReadAllBytes(new File("/foo").toPath))
    filesProxy.reply(ByteString(cfg).toArray)
    (target, probe, filesProxy)
  }
}
