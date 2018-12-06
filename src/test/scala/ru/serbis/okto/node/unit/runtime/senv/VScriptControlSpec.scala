package ru.serbis.okto.node.unit.runtime.senv

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import ru.serbis.okto.node.log.{StdOutLogger, StreamLogger}
import ru.serbis.okto.node.runtime.AppCmdExecutor
import ru.serbis.okto.node.runtime.senv.VScriptControl
import scala.concurrent.duration._

class VScriptControlSpec extends TestKit(ActorSystem("TestSystem")) with ImplicitSender with WordSpecLike with Matchers with BeforeAndAfterAll with StreamLogger {

  override protected def beforeAll(): Unit = {
    initializeGlobalLogger (system)
    logger.addDestination (system.actorOf (StdOutLogger.props) )
  }

  "VScriptControl" must {
    "By exit method must send Exit message to the executor" in {
      val executor = TestProbe()
      val target = new VScriptControl(executor.ref)

      target.exit(10)
      executor.expectMsg(AppCmdExecutor.Commands.Exit(10))
    }

    "By exit method don't send Exit message to the executor if this message was sent early" in {
      val executor = TestProbe()
      val target = new VScriptControl(executor.ref)

      target.exit(10)
      executor.expectMsg(AppCmdExecutor.Commands.Exit(10))
      target.exit(11)
      executor.expectNoMessage(1 second)
    }
  }
}
