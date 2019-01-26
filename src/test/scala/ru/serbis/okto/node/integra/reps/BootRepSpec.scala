package ru.serbis.okto.node.integra.reps

import java.io.File
import java.nio.file.{Files, StandardOpenOption}

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import akka.util.ByteString
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import ru.serbis.okto.node.log.{StdOutLogger, StreamLogger}
import ru.serbis.okto.node.reps.BootRep

import scala.concurrent.duration._

class BootRepSpec extends TestKit(ActorSystem("TestSystem")) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll with StreamLogger {

  val datFile = new File("fect/boot_test.conf")
  val data = "1 {\n\tcmd: \"cmd 1\"\n}\n2 {\n\tcmd: \"cmd 2\"\n}\n3 {\n\tcmd: \"cmd 3\"\n}\n"

  override protected def beforeAll(): Unit = {
    initializeGlobalLogger(system)
    logger.addDestination(system.actorOf(StdOutLogger.props))

    val fect = new File("fect")

    if (!fect.exists()) fect.mkdir()
    if (!datFile.exists()) datFile.createNewFile()
    Files.write(datFile.toPath, ByteString(data).toArray, StandardOpenOption.TRUNCATE_EXISTING)
  }

  def restoreDatFile() = {
    if (!datFile.exists()) datFile.createNewFile()
    Files.write(datFile.toPath, ByteString(data).toArray, StandardOpenOption.TRUNCATE_EXISTING)
  }
  def deleteDataFile() = Files.delete(datFile.toPath)

  "BootRep" must {
    "For GetAll commands must return boot defs list" in {
      val probe = TestProbe()
      val target = system.actorOf(BootRep.props(datFile.getAbsolutePath))
      probe.send(target, BootRep.Commands.GetAll)
      val expectedDefs = List(
        BootRep.Responses.BootDefinition(1, "cmd 1"),
        BootRep.Responses.BootDefinition(2, "cmd 2"),
        BootRep.Responses.BootDefinition(3, "cmd 3")
      )
      probe.expectMsg(expectedDefs)
    }

    "For Create message" should {
      "Write new boot definition to boot.conf and return BootDefinition" in {
        val probe = TestProbe()
        val target = system.actorOf(BootRep.props(datFile.getAbsolutePath, testMode = true))
        probe.send(target, BootRep.Commands.Create("cmd x"))
        probe.expectMsg(BootRep.Responses.BootDefinition(4, "cmd x"))
        ByteString(Files.readAllBytes(datFile.toPath)).utf8String shouldEqual "1 {\n\tcmd: \"cmd 1\"\n}\n2 {\n\tcmd: \"cmd 2\"\n}\n3 {\n\tcmd: \"cmd 3\"\n}\n4 {\n\tcmd: \"cmd x\"\n}\n"
        restoreDatFile()
      }

      "Return WriteError if some file writing error was occurred" in {
        val probe = TestProbe()
        val target = system.actorOf(BootRep.props(datFile.getAbsolutePath))
        probe.send(target, BootRep.Commands.Create("cmd x"))
        probe.expectMsgType[BootRep.Responses.BootDefinition]
        deleteDataFile()
        probe.send(target, BootRep.Commands.Create("cmd x"))
        probe.expectMsg(BootRep.Responses.WriteError)
        restoreDatFile()
      }

      "Stash message if some file operation takes place" in {
        val probe = TestProbe()
        val target = system.actorOf(BootRep.props(datFile.getAbsolutePath, testMode = true))
        probe.send(target, BootRep.Commands.Create("cmd x"))
        probe.send(target, BootRep.Commands.Create("cmd y"))
        probe.expectNoMessage(1 second)
        probe.expectMsg(BootRep.Responses.BootDefinition(4, "cmd x"))
        probe.expectNoMessage(1 second)
        probe.expectMsg(BootRep.Responses.BootDefinition(4, "cmd y"))
        restoreDatFile()
      }
    }

    "For RemoveCommand message" should {
      "Remove boot definition from boot.conf and return Removed" in {
        val probe = TestProbe()
        val target = system.actorOf(BootRep.props(datFile.getAbsolutePath))
        probe.send(target, BootRep.Commands.Remove(2))
        probe.expectMsg(BootRep.Responses.Removed)
        ByteString(Files.readAllBytes(datFile.toPath)).utf8String shouldEqual "1 {\n\tcmd: \"cmd 1\"\n}\n3 {\n\tcmd: \"cmd 3\"\n}\n"
        restoreDatFile()
      }

      "Return NotExist if command doest not exist in usercoms.conf" in {
        val probe = TestProbe()
        val target = system.actorOf(BootRep.props(datFile.getAbsolutePath))
        probe.send(target, BootRep.Commands.Remove(10))
        probe.expectMsg(BootRep.Responses.NotExist)
      }

      "Return WriteError if some file reading error was occurred" in {
        val probe = TestProbe()
        val target = system.actorOf(BootRep.props(datFile.getAbsolutePath))
        probe.send(target, BootRep.Commands.GetAll)
        probe.expectMsgType[List[BootRep.Responses.BootDefinition]]
        deleteDataFile()
        probe.send(target, BootRep.Commands.Remove(2))
        probe.expectMsg(BootRep.Responses.WriteError)
        restoreDatFile()
      }

      "Stash message if some file operation takes place" in {
        val probe = TestProbe()
        val target = system.actorOf(BootRep.props(datFile.getAbsolutePath, testMode = true))
        probe.send(target, BootRep.Commands.Remove(1))
        probe.send(target, BootRep.Commands.Remove(3))
        probe.expectNoMessage(1 second)
        probe.expectMsg(BootRep.Responses.Removed)
        probe.expectNoMessage(1 second)
        probe.expectMsg(BootRep.Responses.Removed)
        restoreDatFile()
      }
    }

    "For DropCache message" should {
      "Clean cached configuration" in {
        val probe = TestProbe()
        val target = system.actorOf(BootRep.props(datFile.getAbsolutePath))
        probe.send(target, BootRep.Commands.GetAll)
        probe.expectMsgType[List[BootRep.Responses.BootDefinition]]
        deleteDataFile()
        probe.send(target, BootRep.Commands.GetAll)
        val expectedDefs = List(
          BootRep.Responses.BootDefinition(1, "cmd 1"),
          BootRep.Responses.BootDefinition(2, "cmd 2"),
          BootRep.Responses.BootDefinition(3, "cmd 3")
        )
        probe.expectMsg(expectedDefs)
        restoreDatFile()
      }
    }
  }
}
