package ru.serbis.okto.node.integra.reps

import java.io.File
import java.nio.file.{Files, StandardOpenOption}

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import akka.util.ByteString
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import ru.serbis.okto.node.log.{StdOutLogger, StreamLogger}
import ru.serbis.okto.node.reps.UsercomsRep
import ru.serbis.okto.node.reps.UsercomsRep.Responses.UserCommandDefinition

import scala.concurrent.duration._

class UsercomsRepSpec extends TestKit(ActorSystem("TestSystem")) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll with StreamLogger {

  val datFile = new File("fect/usercoms_test.conf")
  val data = "a {\n  file = \"class.a\"\n}\nb {\n  file = \"class.b\"\n}\nc {\n  file = \"class.c\"\n}\nd {\n  xxx = \"a\"\n}\n"

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

  "UsercomsRep" must {
    "For GetCommandsBatch message" should {
      "Return correct intersected CommandsBatch response" in {
        val probe = TestProbe()
        val target = system.actorOf(UsercomsRep.props(datFile.getAbsolutePath))
        probe.send(target, UsercomsRep.Commands.GetCommandsBatch(List("a", "x", "b", "y", "c", "z", "d")))
        val exectedDefs = Map(
          "a" -> Some(UserCommandDefinition("a", "class.a", Vector.empty, Vector.empty)),
          "x" -> None,
          "b" -> Some(UserCommandDefinition("b", "class.b", Vector.empty, Vector.empty)),
          "y" -> None,
          "c" -> Some(UserCommandDefinition("c", "class.c", Vector.empty, Vector.empty)),
          "z" -> None,
          "d" -> None
        )
        probe.expectMsg(UsercomsRep.Responses.CommandsBatch(exectedDefs))
      }

      "Return full commands list if empty names list was passed" in {
        val probe = TestProbe()
        val target = system.actorOf(UsercomsRep.props(datFile.getAbsolutePath))
        probe.send(target, UsercomsRep.Commands.GetCommandsBatch(List.empty))
        val exectedDefs = Map(
          "a" -> Some(UserCommandDefinition("a", "class.a", Vector.empty, Vector.empty)),
          "b" -> Some(UserCommandDefinition("b", "class.b", Vector.empty, Vector.empty)),
          "c" -> Some(UserCommandDefinition("c", "class.c", Vector.empty, Vector.empty))
        )
        probe.expectMsg(UsercomsRep.Responses.CommandsBatch(exectedDefs))
      }

      "Return correct commands list if empty names list was passed and some wrong data in config take place" in {
        val nData = "a {\n  file = \"a.js\"\n}\nb {\n  xxx = \"b.js\"\n}\nc {\n  file = \"c.js\"\n}\n"
        if (!datFile.exists()) datFile.createNewFile()
        Files.write(datFile.toPath, ByteString(nData).toArray, StandardOpenOption.TRUNCATE_EXISTING)

        val probe = TestProbe()
        val target = system.actorOf(UsercomsRep.props(datFile.getAbsolutePath))
        probe.send(target, UsercomsRep.Commands.GetCommandsBatch(List.empty))
        val exectedDefs = Map(
          "a" -> Some(UserCommandDefinition("a", "a.js", Vector.empty, Vector.empty)),
          "c" -> Some(UserCommandDefinition("c", "c.js", Vector.empty, Vector.empty))
        )
        probe.expectMsg(UsercomsRep.Responses.CommandsBatch(exectedDefs))
        restoreDatFile()
      }
    }

    "For CreateCommand message" should {
      "Write new command definition to usercoms.conf and return Created" in {
        val probe = TestProbe()
        val target = system.actorOf(UsercomsRep.props(datFile.getAbsolutePath))
        probe.send(target, UsercomsRep.Commands.Create(UserCommandDefinition("x", "x.js", Vector.empty, Vector.empty)))
        probe.expectMsg(UsercomsRep.Responses.Created)
        ByteString(Files.readAllBytes(datFile.toPath)).utf8String shouldEqual "\"a\" {\n  file = \"class.a\"\n}\n\"b\" {\n  file = \"class.b\"\n}\n\"c\" {\n  file = \"class.c\"\n}\n\"d\" {\n  xxx = \"a\"\n}\n\"x\" {\n  file = \"x.js\"\n}"
        restoreDatFile()
      }

      "Return Exist if command already exist in usercoms.conf" in {
        val probe = TestProbe()
        val target = system.actorOf(UsercomsRep.props(datFile.getAbsolutePath))
        probe.send(target, UsercomsRep.Commands.Create(UserCommandDefinition("a", "a.js", Vector.empty, Vector.empty)))
        probe.expectMsg(UsercomsRep.Responses.Exist)
      }

      "Return WriteError if some file writing error was occurred" in {
        val probe = TestProbe()
        val target = system.actorOf(UsercomsRep.props(datFile.getAbsolutePath))
        probe.send(target, UsercomsRep.Commands.GetCommandsBatch(List("a")))
        probe.expectMsgType[UsercomsRep.Responses.CommandsBatch]
        deleteDataFile()
        probe.send(target, UsercomsRep.Commands.Create(UserCommandDefinition("x", "x.js", Vector.empty, Vector.empty)))
        probe.expectMsg(UsercomsRep.Responses.WriteError)
        restoreDatFile()
      }

      "Stash message if some file operation takes place" in {
        val probe = TestProbe()
        val target = system.actorOf(UsercomsRep.props(datFile.getAbsolutePath, testMode = true))
        probe.send(target, UsercomsRep.Commands.Create(UserCommandDefinition("x", "x.js", Vector.empty, Vector.empty)))
        probe.send(target, UsercomsRep.Commands.Create(UserCommandDefinition("y", "y.js", Vector.empty, Vector.empty)))
        probe.expectNoMessage(1 second)
        probe.expectMsg(UsercomsRep.Responses.Created)
        probe.expectNoMessage(1 second)
        probe.expectMsg(UsercomsRep.Responses.Created)
        restoreDatFile()
      }
    }

    "For RemoveCommand message" should {
      "Remove command definition from usercoms.conf and return Removed" in {
        val nData = "a {\n  file = \"a.js\"\n}\nb {\n  file = \"b.js\"\n}\nc {\n  file = \"c.js\"\n}\n"
        if (!datFile.exists()) datFile.createNewFile()
        Files.write(datFile.toPath, ByteString(nData).toArray, StandardOpenOption.TRUNCATE_EXISTING)

        val probe = TestProbe()
        val target = system.actorOf(UsercomsRep.props(datFile.getAbsolutePath))
        probe.send(target, UsercomsRep.Commands.Remove("b"))
        probe.expectMsg(UsercomsRep.Responses.Removed)
        ByteString(Files.readAllBytes(datFile.toPath)).utf8String shouldEqual "a {\n  file = \"a.js\"\n}\nc {\n  file = \"c.js\"\n}\n"
        restoreDatFile()
      }

      "Return NotExist if command doest not exist in usercoms.conf" in {
        val probe = TestProbe()
        val target = system.actorOf(UsercomsRep.props(datFile.getAbsolutePath))
        probe.send(target, UsercomsRep.Commands.Remove("z"))
        probe.expectMsg(UsercomsRep.Responses.NotExist)
      }

      "Return ReadError if some file reading error was occurred" in {
        val nData = "a {\n  file = \"a.js\"\n}\nb {\n  file = \"b.js\"\n}\nc {\n  file = \"c.js\"\n}\n"
        if (!datFile.exists()) datFile.createNewFile()
        Files.write(datFile.toPath, ByteString(nData).toArray, StandardOpenOption.TRUNCATE_EXISTING)

        val probe = TestProbe()
        val target = system.actorOf(UsercomsRep.props(datFile.getAbsolutePath))
        probe.send(target, UsercomsRep.Commands.GetCommandsBatch(List("a")))
        probe.expectMsgType[UsercomsRep.Responses.CommandsBatch]
        deleteDataFile()
        probe.send(target, UsercomsRep.Commands.Remove("a"))
        probe.expectMsg(UsercomsRep.Responses.WriteError)
        restoreDatFile()
      }

      "Stash message if some file operation takes place" in {
        val nData = "a {\n  file = \"a.js\"\n}\nb {\n  file = \"b.js\"\n}\nc {\n  file = \"c.js\"\n}\n"
        if (!datFile.exists()) datFile.createNewFile()
        Files.write(datFile.toPath, ByteString(nData).toArray, StandardOpenOption.TRUNCATE_EXISTING)

        val probe = TestProbe()
        val target = system.actorOf(UsercomsRep.props(datFile.getAbsolutePath, testMode = true))
        probe.send(target, UsercomsRep.Commands.Remove("a"))
        probe.send(target, UsercomsRep.Commands.Remove("b"))
        probe.expectNoMessage(1 second)
        probe.expectMsg(UsercomsRep.Responses.Removed)
        probe.expectNoMessage(1 second)
        probe.expectMsg(UsercomsRep.Responses.Removed)
        restoreDatFile()
      }
    }

    "For DropCache message" should {
      "Clean cached configuration" in {
        val probe = TestProbe()
        val target = system.actorOf(UsercomsRep.props(datFile.getAbsolutePath))
        probe.send(target, UsercomsRep.Commands.GetCommandsBatch(List("a")))
        probe.expectMsgType[UsercomsRep.Responses.CommandsBatch]
        deleteDataFile()
        probe.send(target, UsercomsRep.Commands.DropCache)
        probe.send(target, UsercomsRep.Commands.GetCommandsBatch(List("a")))
        probe.expectMsg(UsercomsRep.Responses.CommandsBatch(Map("a" -> None)))
        restoreDatFile()
      }
    }
  }
}
