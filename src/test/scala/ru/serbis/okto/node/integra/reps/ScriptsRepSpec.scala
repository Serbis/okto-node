package ru.serbis.okto.node.integra.reps

import java.io.File
import java.nio.file.{Files, StandardOpenOption}

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import akka.util.ByteString
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import ru.serbis.okto.node.log.{StdOutLogger, StreamLogger}
import ru.serbis.okto.node.reps.{ScriptsRep, SyscomsRep}
import ru.serbis.okto.node.reps.SyscomsRep.Responses.SystemCommandDefinition
import scala.concurrent.duration._

class ScriptsRepSpec extends TestKit(ActorSystem("TestSystem")) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll with StreamLogger {

  val datFile = new File("fect/ucmd/test.js")
  val data = "xxx"

  override protected def beforeAll(): Unit = {
    initializeGlobalLogger(system)
    logger.addDestination(system.actorOf(StdOutLogger.props))

    val fect = new File("fect")

    if (!fect.exists()) fect.mkdir()
    if (!datFile.exists()) datFile.createNewFile()
    Files.write(datFile.toPath, ByteString(data).toArray, StandardOpenOption.TRUNCATE_EXISTING)
  }

  def restoreTestFile() = {
    datFile.createNewFile()
    Files.write(datFile.toPath, ByteString(data).toArray, StandardOpenOption.TRUNCATE_EXISTING)
  }

  def deleteTestFile() = {
    Files.delete(datFile.toPath)
  }

  "ScriptsRep" must {
    "Read script from disk and cache it code" in {
      val probe = TestProbe()
      val target = system.actorOf(ScriptsRep.props("fect/ucmd", 1000, 1000))

      probe.send(target, ScriptsRep.Commands.GetScript("test.js"))
      probe.expectMsg(ScriptsRep.Responses.Script("xxx"))
      deleteTestFile()
      probe.send(target, ScriptsRep.Commands.GetScript("test.js"))
      probe.expectMsg(ScriptsRep.Responses.Script("xxx"))
      restoreTestFile()
    }

    "Remove data from cache after entry live timeout reached" in {
      val probe = TestProbe()
      val target = system.actorOf(ScriptsRep.props("fect/ucmd", 500, 500))

      probe.send(target, ScriptsRep.Commands.GetScript("test.js"))
      probe.expectMsg(ScriptsRep.Responses.Script("xxx"))
      deleteTestFile()
      expectNoMessage(2 second)
      probe.send(target, ScriptsRep.Commands.GetScript("test.js"))
      probe.expectMsg(ScriptsRep.Responses.ScriptNotFound)
      restoreTestFile()
    }

    "Prolongate cached data time to live after access to in" in {
      val probe = TestProbe()
      val target = system.actorOf(ScriptsRep.props("fect/ucmd", 1000, 100))

      probe.send(target, ScriptsRep.Commands.GetScript("test.js"))
      probe.expectMsg(ScriptsRep.Responses.Script("xxx"))
      deleteTestFile()
      expectNoMessage(0.5 second)
      probe.send(target, ScriptsRep.Commands.GetScript("test.js"))
      probe.expectMsg(ScriptsRep.Responses.Script("xxx"))
      expectNoMessage(0.5 second)
      probe.send(target, ScriptsRep.Commands.GetScript("test.js"))
      probe.expectMsg(ScriptsRep.Responses.Script("xxx"))
      expectNoMessage(0.5 second)

      expectNoMessage(3 second)
      probe.send(target, ScriptsRep.Commands.GetScript("test.js"))
      probe.expectMsg(ScriptsRep.Responses.ScriptNotFound)
      restoreTestFile()
    }

    "Reset cashed data by ResetCache message" in {
      val probe = TestProbe()
      val target = system.actorOf(ScriptsRep.props("fect/ucmd", 60000, 1000))

      probe.send(target, ScriptsRep.Commands.GetScript("test.js"))
      probe.expectMsg(ScriptsRep.Responses.Script("xxx"))
      deleteTestFile()
      probe.send(target, ScriptsRep.Commands.ResetCache("test.js"))
      probe.send(target, ScriptsRep.Commands.GetScript("test.js"))
      probe.expectMsg(ScriptsRep.Responses.ScriptNotFound)
      restoreTestFile()
    }

    "Return ScriptNotFound if file does not exist on the disk" in {
      val probe = TestProbe()
      val target = system.actorOf(ScriptsRep.props("fect/ucmd", 1000, 1000))

      probe.send(target, ScriptsRep.Commands.GetScript("testX.js"))
      probe.expectMsg(ScriptsRep.Responses.ScriptNotFound)
    }

    "Stash GetScript message if script directory is locked" in {
      val probe = TestProbe()
      val target = system.actorOf(ScriptsRep.props("fect/ucmd", 1000, 1000, testMode = true))
      probe.send(target, ScriptsRep.Commands.Create("a", "code"))
      probe.send(target, ScriptsRep.Commands.GetScript("test.js"))
      probe.expectNoMessage(1 second)
      probe.expectMsg(ScriptsRep.Responses.Created)
      probe.expectMsg(ScriptsRep.Responses.Script("xxx"))
    }

    "For Create message" should {
      "Create new script file in the scripts directory" in {
        val file = new File("fect/ucmd/a.js").toPath
        Files.deleteIfExists(file)

        val probe = TestProbe()
        val target = system.actorOf(ScriptsRep.props("fect/ucmd", 1000, 1000))

        probe.send(target, ScriptsRep.Commands.Create("a", "code"))
        probe.expectMsg(ScriptsRep.Responses.Created)

        Files.exists(file) shouldEqual true
        ByteString(Files.readAllBytes(file)).utf8String shouldEqual "code"
      }

      "Replace existing file if it is was early created" in {
        val file = new File("fect/ucmd/a.js").toPath
        Files.deleteIfExists(file)
        Files.createFile(file)

        val probe = TestProbe()
        val target = system.actorOf(ScriptsRep.props("fect/ucmd", 1000, 1000))

        probe.send(target, ScriptsRep.Commands.Create("a", "code"))
        probe.expectMsg(ScriptsRep.Responses.Created)

        Files.exists(file) shouldEqual true
        ByteString(Files.readAllBytes(file)).utf8String shouldEqual "code"
      }
    }

    "For Remove message" should {
      "Remove the script file from the script directory and return Removed" in {
        val file = new File("fect/ucmd/a.js").toPath
        Files.deleteIfExists(file)
        Files.createFile(file)

        val probe = TestProbe()
        val target = system.actorOf(ScriptsRep.props("fect/ucmd", 1000, 1000))

        probe.send(target, ScriptsRep.Commands.Remove("a"))
        probe.expectMsg(ScriptsRep.Responses.Removed)
        Files.exists(file) shouldEqual false
      }

      "Return Removed if the script file does not exist in the scripts directory" in {
        val file = new File("fect/ucmd/a.js").toPath
        Files.deleteIfExists(file)

        val probe = TestProbe()
        val target = system.actorOf(ScriptsRep.props("fect/ucmd", 1000, 1000))

        probe.send(target, ScriptsRep.Commands.Remove("a"))
        probe.expectMsg(ScriptsRep.Responses.Removed)
        Files.exists(file) shouldEqual false
      }
    }
  }
}
