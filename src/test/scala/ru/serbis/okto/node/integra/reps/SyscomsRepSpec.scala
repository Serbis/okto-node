package ru.serbis.okto.node.integra.reps

import java.io.File
import java.nio.file.{Files, StandardOpenOption}

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import akka.util.ByteString
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import ru.serbis.okto.node.log.{StdOutLogger, StreamLogger}
import ru.serbis.okto.node.reps.SyscomsRep.Responses.SystemCommandDefinition
import ru.serbis.okto.node.reps.SyscomsRep

class SyscomsRepSpec extends TestKit(ActorSystem("TestSystem")) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll with StreamLogger {

  val datFile = new File("fect/syscoms_test.conf")
  val data = "a {\n  class = \"class.a\"\n}\nb {\n  class = \"class.b\"\n}\nc {\n  class = \"class.c\"\n}\nd {\n  xxx = \"a\"\n}"

  override protected def beforeAll(): Unit = {
    initializeGlobalLogger(system)
    logger.addDestination(system.actorOf(StdOutLogger.props))

    val fect = new File("fect")

    if (!fect.exists()) fect.mkdir()
    if (!datFile.exists()) datFile.createNewFile()
    Files.write(datFile.toPath, ByteString(data).toArray, StandardOpenOption.TRUNCATE_EXISTING)
  }

  "SyscomsRep" must {
    "For GetCommandsBatch message" should {
      "Return correct intersected CommandsBatch response" in {
        val probe = TestProbe()
        val target = system.actorOf(SyscomsRep.props(datFile.getAbsolutePath))
        probe.send(target, SyscomsRep.Commands.GetCommandsBatch(List("a", "x", "b", "y", "c", "z", "d")))
        val exectedDefs = Map(
          "a" -> Some(SystemCommandDefinition("class.a")),
          "x" -> None,
          "b" -> Some(SystemCommandDefinition("class.b")),
          "y" -> None,
          "c" -> Some(SystemCommandDefinition("class.c")),
          "z" -> None,
          "d" -> None
        )
        probe.expectMsg(SyscomsRep.Responses.CommandsBatch(exectedDefs))
      }
    }
  }
}
