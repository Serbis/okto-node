package ru.serbis.okto.node.integra.hardware

import java.nio.file.Files
import akka.actor.ActorSystem
import akka.actor.SupervisorStrategy.Stop
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import akka.util.ByteString
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import ru.serbis.okto.node.common.NodeUtils
import ru.serbis.okto.node.hardware.SystemDaemon
import ru.serbis.okto.node.log.{StdOutLogger, StreamLogger}

import scala.concurrent.duration._

/** This test performs integration testing of the actor interacting with the system node daemon. To run a test on the system
  * on which testing is performed, a real instance of the NSD daemon must be running. Due to the fact that the daemon
  * controls system-dependent parameters on behalf of the privileged user, its testing capabilities are very limited and
  * boil down to checking the basic parameters of the actor (timeouts, errors, etc.) and executing the only neutral command
  * echo, which, strictly speaking, is intended fot test activities. */
class SystemDaemonSpec extends TestKit(ActorSystem("TestSystem")) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll with StreamLogger {

  override protected def beforeAll(): Unit = {
    NodeUtils.addJniPath("jni")
    System.loadLibrary("Hw_e")

    initializeGlobalLogger(system)
    logger.addDestination(system.actorOf(StdOutLogger.props))
  }

  val socketPath = "/tmp/nsd.socket"

  "SystemDamon" must {
    "For DaemonRequest message" should {
      "Return DaemonResponse if daemon was send correct message" in {
        val probe = TestProbe()
        val target = system.actorOf(SystemDaemon.props(socketPath, 1000, emulatorMode = true))

        probe.send(target, SystemDaemon.Commands.DaemonRequest("t_echo abc", 10))
        probe.expectMsg(SystemDaemon.Responses.DaemonResponse("abc", 10))
        target ! Stop
      }

      "Return RequestTimeout if device does not respond with expected timeout" in {
        val probe = TestProbe()
        val target = system.actorOf(SystemDaemon.props(socketPath, 1000, emulatorMode = true))

        probe.send(target, SystemDaemon.Commands.DaemonRequest("t_tmt 2000", 10))
        probe.expectMsg(SystemDaemon.Responses.ResponseTimeout(10))
        target ! Stop
      }

      "Return HardwareError if device was respond with some error" in {
        val probe = TestProbe()
        val target = system.actorOf(SystemDaemon.props(socketPath, 1000, emulatorMode = true))

        probe.send(target, SystemDaemon.Commands.DaemonRequest("t_err error", 10))
        probe.expectMsg(SystemDaemon.Responses.DaemonError("error", 10))
        target ! Stop
      }

      "Return BufferOverload if request table contained max request value" in {
        val probe = TestProbe()
        val target = system.actorOf(SystemDaemon.props(socketPath, 1, emulatorMode = true))

        probe.send(target, SystemDaemon.Commands.DaemonRequest("t_tmt 2000", 10))
        probe.send(target, SystemDaemon.Commands.DaemonRequest("t_tmt 2000", 10))
        probe.expectMsg(SystemDaemon.Responses.DaemonOverload)
        target ! Stop
      }

      "Process without fail response from device that's was corrupted" in {
        val probe = TestProbe()
        val target = system.actorOf(SystemDaemon.props(socketPath, 1000, emulatorMode = true))

        probe.send(target, SystemDaemon.Commands.DaemonRequest("t_re 0", 10))
        probe.expectMsg(SystemDaemon.Responses.ResponseTimeout(10))


        probe.send(target, SystemDaemon.Commands.DaemonRequest("t_re 1", 11))
        probe.expectMsg(SystemDaemon.Responses.ResponseTimeout(11))

        probe.send(target, SystemDaemon.Commands.DaemonRequest("t_re 2", 12))
        probe.expectMsg(SystemDaemon.Responses.ResponseTimeout(12))

        probe.send(target, SystemDaemon.Commands.DaemonRequest("t_re 3", 13))
        probe.expectMsg(SystemDaemon.Responses.ResponseTimeout(13))

        target ! Stop
      }
    }
  }
}