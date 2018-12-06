package ru.serbis.okto.node.integra.hardware

import java.io.File
import java.nio.file.Files
import akka.actor.ActorSystem
import akka.actor.SupervisorStrategy.Stop
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import akka.util.ByteString
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import ru.serbis.okto.node.common.NodeUtils
import ru.serbis.okto.node.hardware.SerialBridge
import ru.serbis.okto.node.log.{StdOutLogger, StreamLogger}
import scala.concurrent.duration._

/** The test procedure is based on the serial port emulation mode of the target actor. In detail, this technique is
  * described in the actor. In general, in this mode, the bridge writes and reads data into two physically separated
  * regular files with the _rx and _tx prefixes. In _tx get data that the bridge will write to the serial port. And
  * the data written in the test in _rx will be read by the bridge as data received from the serial port. To run this
  * test, you need a special version of the native library libHw, adapted to work with regular files instead of a real
  * serial port */
class SerialBridgeSpec extends TestKit(ActorSystem("TestSystem")) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll with StreamLogger {

  override protected def beforeAll(): Unit = {
    NodeUtils.addJniPath("jni")
    System.loadLibrary("Hw_e")

    initializeGlobalLogger(system)
    logger.addDestination(system.actorOf(StdOutLogger.props))
  }

  "SerialBridge" must {
    "For SerialRequest message" should {
      "Return SerialResponse if device was send correct message" in {
        val em = "fect/u1"
        val rxFilePath = new File("fect/u1_rx").toPath
        val txFilePath = new File("fect/u1_tx").toPath

        val probe = TestProbe()
        val delay = TestProbe()
        val target = system.actorOf(SerialBridge.props(em, 115200, 1000, emulatorMode = true))

        probe.send(target, SerialBridge.Commands.SerialRequest("abc", 10))
        delay.expectNoMessage(1 second)
        ByteString(Files.readAllBytes(txFilePath)).utf8String shouldEqual "c\t1\tabc\r"
        Files.write(rxFilePath, ByteString("r\t1\tzzz\r").toArray)
        probe.expectMsg(SerialBridge.Responses.SerialResponse("zzz", 10))
        target ! Stop
        delay.expectNoMessage(1 second)
      }

      "Return RequestTimeout if device does not respond with expected timeout" in {
        val em = "fect/u2"
        val txFilePath = new File("fect/u2_tx").toPath

        val probe = TestProbe()
        val delay = TestProbe()
        val target = system.actorOf(SerialBridge.props(em, 115200, 1000, emulatorMode = true))

        probe.send(target, SerialBridge.Commands.SerialRequest("abc", 10))
        delay.expectNoMessage(1 second)
        ByteString(Files.readAllBytes(txFilePath)).utf8String shouldEqual "c\t1\tabc\r"
        probe.expectMsg(SerialBridge.Responses.ResponseTimeout(10))
        delay.expectNoMessage(1 second)
      }

      "Return HardwareError if device was respond with some error" in {
        val em = "fect/u3"
        val rxFilePath = new File("fect/u3_rx").toPath
        val txFilePath = new File("fect/u3_tx").toPath

        val probe = TestProbe()
        val delay = TestProbe()
        val target = system.actorOf(SerialBridge.props(em, 115200, 1000, emulatorMode = true))

        probe.send(target, SerialBridge.Commands.SerialRequest("abc", 10))
        delay.expectNoMessage(1 second)
        ByteString(Files.readAllBytes(txFilePath)).utf8String shouldEqual "c\t1\tabc\r"
        Files.write(rxFilePath, ByteString("e\t1\terror\r").toArray)
        probe.expectMsg(SerialBridge.Responses.HardwareError("error", 10))
        delay.expectNoMessage(1 second)
      }

      "Return BufferOverload if request table contained max request value" in {
        val em = "fect/u4"

        val probe = TestProbe()
        val delay = TestProbe()
        val target = system.actorOf(SerialBridge.props(em, 115200, 1, emulatorMode = true))

        probe.send(target, SerialBridge.Commands.SerialRequest("abc", 10))
        probe.send(target, SerialBridge.Commands.SerialRequest("abc", 10))
        probe.expectMsg(SerialBridge.Responses.BridgeOverload)
        delay.expectNoMessage(1 second)
      }

      "Process without fail response from device that's was corrupted" in {
        val em = "fect/u5"
        val rxFile = new File("fect/u5_rx")
        val txFile = new File("fect/u5_tx")
        val rxFilePath = rxFile.toPath
        val txFilePath = txFile.toPath

        val probe = TestProbe()
        val delay = TestProbe()
        val target = system.actorOf(SerialBridge.props(em, 115200, 1000, emulatorMode = true))

        probe.send(target, SerialBridge.Commands.SerialRequest("abc", 10))
        delay.expectNoMessage(1 second)
        ByteString(Files.readAllBytes(txFilePath)).utf8String shouldEqual "c\t1\tabc\r"
        Files.write(rxFilePath, ByteString("_\t1\terror\r").toArray) //Неверный квалификатор
        probe.expectMsg(SerialBridge.Responses.ResponseTimeout(10))


        probe.send(target, SerialBridge.Commands.SerialRequest("abc", 11))
        delay.expectNoMessage(1 second)
        ByteString(Files.readAllBytes(txFilePath)).utf8String shouldEqual "c\t1\tabc\r"
        Files.write(rxFilePath, ByteString("r\terror\r").toArray) //Неверная стрктура
        probe.expectMsg(SerialBridge.Responses.ResponseTimeout(11))

        probe.send(target, SerialBridge.Commands.SerialRequest("abc", 12))
        delay.expectNoMessage(1 second)
        ByteString(Files.readAllBytes(txFilePath)).utf8String shouldEqual "c\t1\tabc\r"
        Files.write(rxFilePath, ByteString("r\ta\terror\r").toArray) //Битый блок msgid
        probe.expectMsg(SerialBridge.Responses.ResponseTimeout(12))

        probe.send(target, SerialBridge.Commands.SerialRequest("abc", 13))
        delay.expectNoMessage(1 second)
        ByteString(Files.readAllBytes(txFilePath)).utf8String shouldEqual "c\t1\tabc\r"
        Files.write(rxFilePath, ByteString("r\t1000\terror\r").toArray) //Несуществующий msgid
        probe.expectMsg(SerialBridge.Responses.ResponseTimeout(13))

        probe.send(target, SerialBridge.Commands.SerialRequest("abc", 14))
        delay.expectNoMessage(1 second)
        ByteString(Files.readAllBytes(txFilePath)).utf8String shouldEqual "c\t1\tabc\r"
        Files.write(rxFilePath, ByteString("r\t1\tok\r").toArray) //Корректный ответ
        probe.expectMsg(SerialBridge.Responses.SerialResponse("ok", 14))

        delay.expectNoMessage(1 second)
      }
    }
  }
}