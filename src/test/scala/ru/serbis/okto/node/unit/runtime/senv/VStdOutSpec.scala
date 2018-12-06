package ru.serbis.okto.node.unit.runtime.senv

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import akka.util.ByteString
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import ru.serbis.okto.node.log.{StdOutLogger, StreamLogger}
import ru.serbis.okto.node.runtime.senv.VStdOut
import ru.serbis.okto.node.runtime.{AppCmdExecutor, StreamControls, Stream}

class VStdOutSpec extends TestKit(ActorSystem("TestSystem")) with ImplicitSender with WordSpecLike with Matchers with BeforeAndAfterAll with StreamLogger {

  override protected def beforeAll(): Unit = {
    initializeGlobalLogger (system)
    logger.addDestination (system.actorOf (StdOutLogger.props) )
  }

  "VStdOut" must {
    "By write method must send StdOutWrite message to stdOut" in {
      val executor = TestProbe()
      val stdOut = TestProbe()
      val target = new VStdOut(executor.ref, stdOut.ref)

      //target.write("abc")
      //executor.expectMsg(AppCmdExecutor.Commands.StdOutWrite(ByteString("abc")))
      target.write("abc")
      stdOut.expectMsg(Stream.Commands.WriteWrapped(ByteString("abc") ++ ByteString(StreamControls.EOI)))
    }

    "By writeControlChar method must send char in StdOutWrite message to stdOut" in {
      val executor = TestProbe()
      val stdOut = TestProbe()
      val target = new VStdOut(executor.ref, stdOut.ref)

      //target.write("abc")
      //executor.expectMsg(AppCmdExecutor.Commands.StdOutWrite(ByteString("abc")))
      target.writeControlChar(StreamControls.EOF)
      stdOut.expectMsg(Stream.Commands.WriteWrapped(ByteString(StreamControls.EOF)))
    }
  }
}
