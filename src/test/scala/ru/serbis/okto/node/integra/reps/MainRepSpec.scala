package ru.serbis.okto.node.integra.reps

import java.io.File
import java.nio.file.{Files, StandardOpenOption}

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import akka.util.ByteString
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import ru.serbis.okto.node.log.{Logger, StdOutLogger, StreamLogger}
import ru.serbis.okto.node.reps.{MainRep}

class MainRepSpec extends TestKit(ActorSystem("TestSystem")) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll with StreamLogger {

  val datFile = new File("/usr/share/node/node_test.conf")
  val data = "node {\n  log {\n    level : DEBUG\n    keys : [\"a\", \"b\", \"c\"]\n    file : \"/usr/share/node/node.log\"\n    fileTruncate: false\n  }\n\n  shell {\n    host: \"10.200.0.1\"\n    port: 9999\n  }\nhardware {\n    uart {\n      device: \"/dev/xxx\"\n  maxReq: 999\n      responseCleanInterval: 1111\n    baud: 100000\n      emulation: true\n    }\n  }\n}"

  override protected def beforeAll(): Unit = {
    initializeGlobalLogger(system)
    logger.addDestination(system.actorOf(StdOutLogger.props))

    val fect = new File("fect")

    if (!fect.exists()) fect.mkdir()
    if (!datFile.exists()) datFile.createNewFile()
    Files.write(datFile.toPath, ByteString(data).toArray, StandardOpenOption.TRUNCATE_EXISTING)
  }

  "MainRep" must {
    "For GetLogConfiguration message" should {
      "For config->log->level param" should {
        "Return debug log level value for correct config" in {
          val probe = TestProbe()
          val target = system.actorOf(MainRep.props(datFile.getAbsolutePath))
          probe.send(target, MainRep.Commands.GetLogConfiguration)
          val lc = probe.expectMsgType[MainRep.Responses.LogConfiguration]
          lc.level shouldEqual Logger.LogLevels.Debug
        }

        "Return info log level value for incorrect value" in {
          val probe = TestProbe()
          val target = system.actorOf(MainRep.props(datFile.getAbsolutePath))

          Files.write(datFile.toPath, ByteString("node {\n  log {\n    level: XXX\n    keys: [\"a\", \"b\", \"c\"]\n    file: \"/usr/share/node/node.log\"\n    c: true\n  }\n}").toArray, StandardOpenOption.TRUNCATE_EXISTING)
          probe.send(target, MainRep.Commands.GetLogConfiguration)
          val lc = probe.expectMsgType[MainRep.Responses.LogConfiguration]
          lc.level shouldEqual Logger.LogLevels.Info
          Files.write(datFile.toPath, ByteString(data).toArray, StandardOpenOption.TRUNCATE_EXISTING)
        }

        "Return info log level value for not-exists param" in {
          val probe = TestProbe()
          val target = system.actorOf(MainRep.props(datFile.getAbsolutePath))

          Files.write(datFile.toPath, ByteString("node {\n  log {\n    keys: [\"a\", \"b\", \"c\"]\n    file: \"/usr/share/node/node.log\"\n    fileTruncate: true\n  }\n}").toArray, StandardOpenOption.TRUNCATE_EXISTING)
          probe.send(target, MainRep.Commands.GetLogConfiguration)
          val lc = probe.expectMsgType[MainRep.Responses.LogConfiguration]
          lc.level shouldEqual Logger.LogLevels.Info
          Files.write(datFile.toPath, ByteString(data).toArray, StandardOpenOption.TRUNCATE_EXISTING)
        }
      }

      "For config->log->keys param" should {
        "Return correct key seq for correct config" in {
          val probe = TestProbe()
          val target = system.actorOf(MainRep.props(datFile.getAbsolutePath))
          probe.send(target, MainRep.Commands.GetLogConfiguration)
          val lc = probe.expectMsgType[MainRep.Responses.LogConfiguration]
          lc.keys shouldEqual Seq("a", "b", "c")
        }

        "Return empty seq for not-exists param" in {
          val probe = TestProbe()
          val target = system.actorOf(MainRep.props(datFile.getAbsolutePath))
          Files.write(datFile.toPath, ByteString("node {\n  log {\n    level: DEBUG\n    file: \"/usr/share/node/node.log\"\n    fileTruncate: true\n  }\n}").toArray, StandardOpenOption.TRUNCATE_EXISTING)
          probe.send(target, MainRep.Commands.GetLogConfiguration)
          val lc = probe.expectMsgType[MainRep.Responses.LogConfiguration]
          lc.keys shouldEqual Seq.empty
          Files.write(datFile.toPath, ByteString(data).toArray, StandardOpenOption.TRUNCATE_EXISTING)
        }
      }

      "For config->log->file param" should {
        "Return correct path for correct config" in {
          val probe = TestProbe()
          val target = system.actorOf(MainRep.props(datFile.getAbsolutePath))
          probe.send(target, MainRep.Commands.GetLogConfiguration)
          val lc = probe.expectMsgType[MainRep.Responses.LogConfiguration]
          lc.path shouldEqual new File("/usr/share/node/node.log").toPath
        }

        "Return default path for not-exists param" in {
          val probe = TestProbe()
          val target = system.actorOf(MainRep.props(datFile.getAbsolutePath))
          Files.write(datFile.toPath, ByteString("node {\n  log {\n    level: DEBUG\n    keys: [\"a\", \"b\", \"c\"]\n    fileTruncate: true\n  }\n}").toArray, StandardOpenOption.TRUNCATE_EXISTING)
          probe.send(target, MainRep.Commands.GetLogConfiguration)
          val lc = probe.expectMsgType[MainRep.Responses.LogConfiguration]
          lc.path shouldEqual new File("/tmp/node.log").toPath
          Files.write(datFile.toPath, ByteString(data).toArray, StandardOpenOption.TRUNCATE_EXISTING)
        }
      }

      "For config->log->fileTruncate param" should {
        "Return correct value for correct config" in {
          val probe = TestProbe()
          val target = system.actorOf(MainRep.props(datFile.getAbsolutePath))
          probe.send(target, MainRep.Commands.GetLogConfiguration)
          val lc = probe.expectMsgType[MainRep.Responses.LogConfiguration]
          lc.truncate shouldEqual false
        }

        "Return true for not-exists param" in {
          val probe = TestProbe()
          val target = system.actorOf(MainRep.props(datFile.getAbsolutePath))
          Files.write(datFile.toPath, ByteString("node {\n  log {\n    level: DEBUG\n    keys: [\"a\", \"b\", \"c\"]\n    file: \"/usr/share/node/node.log\"\n    }\n}").toArray, StandardOpenOption.TRUNCATE_EXISTING)
          probe.send(target, MainRep.Commands.GetLogConfiguration)
          val lc = probe.expectMsgType[MainRep.Responses.LogConfiguration]
          lc.truncate shouldEqual true
          Files.write(datFile.toPath, ByteString(data).toArray, StandardOpenOption.TRUNCATE_EXISTING)
        }
      }
    }

    "For GetShellConfiguration message" should {
      "For config->shell->host param" should {
        "Return '10.200.0.1' for correct config" in {
          val probe = TestProbe()
          val target = system.actorOf(MainRep.props(datFile.getAbsolutePath))
          probe.send(target, MainRep.Commands.GetShellConfiguration)
          val sc = probe.expectMsgType[MainRep.Responses.ShellConfiguration]
          sc.host shouldEqual "10.200.0.1"
        }

        "Return '127.0.0.1' for not-exists param" in {
          val probe = TestProbe()
          val target = system.actorOf(MainRep.props(datFile.getAbsolutePath))

          Files.write(datFile.toPath, ByteString("node {\n  shell {\n    port: 5000\n  }\n}").toArray, StandardOpenOption.TRUNCATE_EXISTING)
          probe.send(target, MainRep.Commands.GetShellConfiguration)
          val sc = probe.expectMsgType[MainRep.Responses.ShellConfiguration]
          sc.host shouldEqual "127.0.0.1"
          Files.write(datFile.toPath, ByteString(data).toArray, StandardOpenOption.TRUNCATE_EXISTING)
        }
      }

      "For config->shell->port param" should {
        "Return '9999' for correct config" in {
          val probe = TestProbe()
          val target = system.actorOf(MainRep.props(datFile.getAbsolutePath))
          probe.send(target, MainRep.Commands.GetShellConfiguration)
          val sc = probe.expectMsgType[MainRep.Responses.ShellConfiguration]
          sc.port shouldEqual 9999
        }

        "Return '5000' for incorrect value" in {
          val probe = TestProbe()
          val target = system.actorOf(MainRep.props(datFile.getAbsolutePath))

          Files.write(datFile.toPath, ByteString("node {\n  shell {\n    host: \"10.200.0.1\"\n    port: \"xxx\"\n  }\n}").toArray, StandardOpenOption.TRUNCATE_EXISTING)
          probe.send(target, MainRep.Commands.GetShellConfiguration)
          val sc = probe.expectMsgType[MainRep.Responses.ShellConfiguration]
          sc.port shouldEqual 5000
          Files.write(datFile.toPath, ByteString(data).toArray, StandardOpenOption.TRUNCATE_EXISTING)
        }

        "Return '5000' for not-exists param" in {
          val probe = TestProbe()
          val target = system.actorOf(MainRep.props(datFile.getAbsolutePath))

          Files.write(datFile.toPath, ByteString("node {\n  shell {\n    host: \"10.200.0.1\"\n    }\n}").toArray, StandardOpenOption.TRUNCATE_EXISTING)
          probe.send(target, MainRep.Commands.GetShellConfiguration)
          val sc = probe.expectMsgType[MainRep.Responses.ShellConfiguration]
          sc.port shouldEqual 5000
          Files.write(datFile.toPath, ByteString(data).toArray, StandardOpenOption.TRUNCATE_EXISTING)
        }
      }
    }

    "For GetUartConfiguration message" should {
      "For config->hardware->uart->device param" should {
        "Return '/dev/xxx' for correct config" in {
          val probe = TestProbe()
          val target = system.actorOf(MainRep.props(datFile.getAbsolutePath))
          probe.send(target, MainRep.Commands.GetHardwareConfiguration)
          val sc = probe.expectMsgType[MainRep.Responses.HardwareConfiguration]
          sc.uartConfiguration.device shouldEqual "/dev/xxx"
        }

        "Return '127.0.0.1' for not-exists param" in {
          val probe = TestProbe()
          val target = system.actorOf(MainRep.props(datFile.getAbsolutePath))

          Files.write(datFile.toPath, ByteString("node {\nhardware {\nuart{\nbaud: 100000\nmaxReq: 999\nresponseCleanInterval: 1111\nemulation: true\n}\n}\n}").toArray, StandardOpenOption.TRUNCATE_EXISTING)
          probe.send(target, MainRep.Commands.GetHardwareConfiguration)
          val sc = probe.expectMsgType[MainRep.Responses.HardwareConfiguration]
          sc.uartConfiguration.device shouldEqual "/dev/ttyS1"
          Files.write(datFile.toPath, ByteString(data).toArray, StandardOpenOption.TRUNCATE_EXISTING)
        }
      }

      "For config->hardware->uart->baud param" should {
        "Return '1000000' for correct config" in {
          val probe = TestProbe()
          val target = system.actorOf(MainRep.props(datFile.getAbsolutePath))
          probe.send(target, MainRep.Commands.GetHardwareConfiguration)
          val sc = probe.expectMsgType[MainRep.Responses.HardwareConfiguration]
          sc.uartConfiguration.baud shouldEqual 100000
        }

        "Return '115200' for incorrect value" in {
          val probe = TestProbe()
          val target = system.actorOf(MainRep.props(datFile.getAbsolutePath))

          //TODO [5] если в файле конфигурации забыть в конце одну из скобок, то репозиторий впадает в ступор и ничего не возвращает
          Files.write(datFile.toPath, ByteString("node {hardware {\nuart{\ndevice: \"/dev/xxx\"\nbaud: \"xxx\"\nmaxReq: 999\nresponseCleanInterval: 1111\nemulation: true\n}\n}\n}").toArray, StandardOpenOption.TRUNCATE_EXISTING)
          probe.send(target, MainRep.Commands.GetHardwareConfiguration)
          val sc = probe.expectMsgType[MainRep.Responses.HardwareConfiguration]
          sc.uartConfiguration.baud shouldEqual 115200
          Files.write(datFile.toPath, ByteString(data).toArray, StandardOpenOption.TRUNCATE_EXISTING)
        }

        "Return '115200' for not-exists param" in {
          val probe = TestProbe()
          val target = system.actorOf(MainRep.props(datFile.getAbsolutePath))

          Files.write(datFile.toPath, ByteString("node {hardware {\nuart {\ndevice: \"/dev/xxx\"\nmaxReq: 999\nresponseCleanInterval: 1111\nemulation: true\n}\n}\n}").toArray, StandardOpenOption.TRUNCATE_EXISTING)
          probe.send(target, MainRep.Commands.GetHardwareConfiguration)
          val sc = probe.expectMsgType[MainRep.Responses.HardwareConfiguration]
          sc.uartConfiguration.baud shouldEqual 115200
          Files.write(datFile.toPath, ByteString(data).toArray, StandardOpenOption.TRUNCATE_EXISTING)
        }
      }

      //-------------

      "For config->hardware->uart->maxReq param" should {
        "Return '999' for correct config" in {
          val probe = TestProbe()
          val target = system.actorOf(MainRep.props(datFile.getAbsolutePath))
          probe.send(target, MainRep.Commands.GetHardwareConfiguration)
          val sc = probe.expectMsgType[MainRep.Responses.HardwareConfiguration]
          sc.uartConfiguration.maxReq shouldEqual 999
        }

        "Return '50' for incorrect value" in {
          val probe = TestProbe()
          val target = system.actorOf(MainRep.props(datFile.getAbsolutePath))

          Files.write(datFile.toPath, ByteString("node {hardware {\nuart{\ndevice: \"/dev/xxx\"\nbaud: \"xxx\"\nmaxReq: \"xxx\"\nresponseCleanInterval: 1111\nemulation: true\n}\n}\n}").toArray, StandardOpenOption.TRUNCATE_EXISTING)
          probe.send(target, MainRep.Commands.GetHardwareConfiguration)
          val sc = probe.expectMsgType[MainRep.Responses.HardwareConfiguration]
          sc.uartConfiguration.maxReq shouldEqual 50
          Files.write(datFile.toPath, ByteString(data).toArray, StandardOpenOption.TRUNCATE_EXISTING)
        }

        "Return '50' for not-exists param" in {
          val probe = TestProbe()
          val target = system.actorOf(MainRep.props(datFile.getAbsolutePath))

          Files.write(datFile.toPath, ByteString("node {hardware {\nuart {\ndevice: \"/dev/xxx\"\nresponseCleanInterval: 1111\nemulation: true\n}\n}\n}").toArray, StandardOpenOption.TRUNCATE_EXISTING)
          probe.send(target, MainRep.Commands.GetHardwareConfiguration)
          val sc = probe.expectMsgType[MainRep.Responses.HardwareConfiguration]
          sc.uartConfiguration.maxReq shouldEqual 50
          Files.write(datFile.toPath, ByteString(data).toArray, StandardOpenOption.TRUNCATE_EXISTING)
        }
      }

      "For config->hardware->uart->responseCleanInterval param" should {
        "Return '1111' for correct config" in {
          val probe = TestProbe()
          val target = system.actorOf(MainRep.props(datFile.getAbsolutePath))
          probe.send(target, MainRep.Commands.GetHardwareConfiguration)
          val sc = probe.expectMsgType[MainRep.Responses.HardwareConfiguration]
          sc.uartConfiguration.responseCleanInterval shouldEqual 1111
        }

        "Return '1000' for incorrect value" in {
          val probe = TestProbe()
          val target = system.actorOf(MainRep.props(datFile.getAbsolutePath))

          Files.write(datFile.toPath, ByteString("node {hardware {\nuart{\ndevice: \"/dev/xxx\"\nbaud: \"xxx\"\nmaxReq: 999\nresponseCleanInterval: \"xxx\"\nemulation: true\n}\n}\n}").toArray, StandardOpenOption.TRUNCATE_EXISTING)
          probe.send(target, MainRep.Commands.GetHardwareConfiguration)
          val sc = probe.expectMsgType[MainRep.Responses.HardwareConfiguration]
          sc.uartConfiguration.responseCleanInterval shouldEqual 1000
          Files.write(datFile.toPath, ByteString(data).toArray, StandardOpenOption.TRUNCATE_EXISTING)
        }

        "Return '1000' for not-exists param" in {
          val probe = TestProbe()
          val target = system.actorOf(MainRep.props(datFile.getAbsolutePath))

          Files.write(datFile.toPath, ByteString("node {hardware {\nuart {\ndevice: \"/dev/xxx\"\nmaxReq: 999\nemulation: true\n}\n}\n}").toArray, StandardOpenOption.TRUNCATE_EXISTING)
          probe.send(target, MainRep.Commands.GetHardwareConfiguration)
          val sc = probe.expectMsgType[MainRep.Responses.HardwareConfiguration]
          sc.uartConfiguration.responseCleanInterval shouldEqual 1000
          Files.write(datFile.toPath, ByteString(data).toArray, StandardOpenOption.TRUNCATE_EXISTING)
        }
      }


      //-------------

      "For config->hardware->uart->emulate param" should {
        "Return 'true' for correct config" in {
          val probe = TestProbe()
          val target = system.actorOf(MainRep.props(datFile.getAbsolutePath))
          probe.send(target, MainRep.Commands.GetHardwareConfiguration)
          val sc = probe.expectMsgType[MainRep.Responses.HardwareConfiguration]
          sc.emulation shouldEqual true
        }

        "Return 'false' for incorrect value" in {
          val probe = TestProbe()
          val target = system.actorOf(MainRep.props(datFile.getAbsolutePath))

          Files.write(datFile.toPath, ByteString("node {hardware {\nuart {\ndevice: \"/dev/xxx\"\nbaud: 100000\nmaxReq: 999\nresponseCleanInterval: 1111\nemulation: \"xxx\"\n}\n }\n}").toArray, StandardOpenOption.TRUNCATE_EXISTING)
          probe.send(target, MainRep.Commands.GetHardwareConfiguration)
          val sc = probe.expectMsgType[MainRep.Responses.HardwareConfiguration]
          sc.emulation shouldEqual false
          Files.write(datFile.toPath, ByteString(data).toArray, StandardOpenOption.TRUNCATE_EXISTING)
        }

        "Return 'false' for not-exists param" in {
          val probe = TestProbe()
          val target = system.actorOf(MainRep.props(datFile.getAbsolutePath))

          Files.write(datFile.toPath, ByteString("node {hardware {\nuart {\ndevice: \"/dev/xxx\"\nmaxReq: 999\nresponseCleanInterval: 1111\nbaud: 100000\n}\n }\n}").toArray, StandardOpenOption.TRUNCATE_EXISTING)
          probe.send(target, MainRep.Commands.GetHardwareConfiguration)
          val sc = probe.expectMsgType[MainRep.Responses.HardwareConfiguration]
          sc.emulation shouldEqual false
          Files.write(datFile.toPath, ByteString(data).toArray, StandardOpenOption.TRUNCATE_EXISTING)
        }
      }
    }
  }
}
