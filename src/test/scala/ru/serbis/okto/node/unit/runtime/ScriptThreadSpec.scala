package ru.serbis.okto.node.unit.runtime

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import akka.util.ByteString
import javax.script.ScriptEngineManager
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import ru.serbis.okto.node.common.{CommandsUnion, Env}
import ru.serbis.okto.node.log.{StdOutLogger, StreamLogger}
import ru.serbis.okto.node.reps.SyscomsRep.Responses.SystemCommandDefinition
import ru.serbis.okto.node.runtime.StreamControls.EOF
import ru.serbis.okto.node.runtime._
import ru.serbis.okto.node.runtime.app.ScriptThread

class ScriptThreadSpec extends TestKit(ActorSystem("TestSystem")) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll with StreamLogger {

  override protected def beforeAll(): Unit = {
    initializeGlobalLogger(system)
    logger.addDestination(system.actorOf(StdOutLogger.props))
  }

  "ScriptThread" must {
    "For preproc method must return preprocessed script code" in {
      val origCode = """import * as z from "modb";import { foo as x, bar as y } from "moda";function main(){exit(0);}"""
      val expectedCode = "eval('var z = {' + runtime.import('modb', '*') + '}');\neval('var x = {' + runtime.import('moda', 'foo') + '}');\neval('var y = {' + runtime.import('moda', 'bar') + '}');\nfunction main(){exit(0);}\nmain(['1', '2']);\nprogram.exit(0);"
      val t = new ScriptThread(null, null, origCode, Vector("1", "2"))
      val r = t.preproc()
      r shouldEqual expectedCode
    }
  }
}
