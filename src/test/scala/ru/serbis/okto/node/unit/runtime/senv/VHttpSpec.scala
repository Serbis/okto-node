package ru.serbis.okto.node.unit.runtime.senv

import akka.actor.ActorSystem
import akka.http.scaladsl.model.headers.RawHeader
import akka.http.scaladsl.model._
import akka.http.scaladsl.server.ContentNegotiator.Alternative.ContentType
import akka.stream.ActorMaterializer
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import javax.script.ScriptEngineManager
import jdk.nashorn.api.scripting.ScriptObjectMirror
import jdk.nashorn.internal.runtime.ScriptObject
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import ru.serbis.okto.node.hardware.SystemDaemon
import ru.serbis.okto.node.log.{StdOutLogger, StreamLogger}
import ru.serbis.okto.node.proxy.http.TestHttpProxy
import ru.serbis.okto.node.runtime.senv.{VHttp, VNsd}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future, Promise}

class VHttpSpec extends TestKit(ActorSystem("TestSystem")) with ImplicitSender with WordSpecLike with Matchers with BeforeAndAfterAll with StreamLogger {

  override protected def beforeAll(): Unit = {
    initializeGlobalLogger(system)
    logger.addDestination(system.actorOf(StdOutLogger.props))
  }

  "VHttp" must {
    "Send request, wait response and return it" in {
      val httpProbe = TestProbe()
      val httpProxy = new TestHttpProxy(httpProbe.ref)

      val target = new VHttp(httpProxy, ActorMaterializer.create(system))

      val r = Future {
        target.req("POST", "https://google.com", "application/json", "xxx", Array("Session", "abc"), 1000, response = true)
      }

      val req = HttpRequest(method = HttpMethods.POST, uri = "https://google.com", entity = HttpEntity(ContentTypes.`application/json`, "xxx")).withHeaders(RawHeader("Session", "abc"))
      httpProbe.expectMsg(TestHttpProxy.Actions.SingleRequest(req))
      val p: Promise[HttpResponse] = Promise()
      httpProbe.reply(p.future)
      p.success(HttpResponse(entity = HttpEntity(ContentTypes.`text/plain(UTF-8)`, "yyy")))
      val fr = Await.result(r, 1 second)
      fr.error shouldEqual 0
      fr.code shouldEqual 200
      fr.result shouldEqual "yyy"

    }

    "Send request and returnt stub if result does not needed" in {
      val httpProbe = TestProbe()
      val httpProxy = new TestHttpProxy(httpProbe.ref)

      val target = new VHttp(httpProxy, ActorMaterializer.create(system))

      val r = Future {
        target.req("POST", "https://google.com", "application/json", "xxx", Array("Session", "abc"), 1000, response = false)
      }
      val req = HttpRequest(method = HttpMethods.POST, uri = "https://google.com", entity = HttpEntity(ContentTypes.`application/json`, "xxx")).withHeaders(RawHeader("Session", "abc"))
      httpProbe.expectMsg(TestHttpProxy.Actions.SingleRequest(req))
      val p: Promise[HttpResponse] = Promise()
      httpProbe.reply(p.future)

      val fr = Await.result(r, 1 second)
      fr.error shouldEqual 0
      fr.code shouldEqual 0
      fr.result shouldEqual ""
    }

    "Return error some of parameters is null" in {
      val target = new VHttp(null, null)
      val err1 = target.req(null, "", "", "", Array.empty, 1000, response = false)
      val err2 = target.req("", null, "", "", Array.empty, 1000, response = false)
      val err3 = target.req("", "", null, "", Array.empty, 1000, response = false)
      val err4 = target.req("", "", "", null, Array.empty, 1000, response = false)
      val err5 = target.req("", "", "", "", null, 1000, response = false)
      err1.error shouldEqual 1
      err2.error shouldEqual 1
      err3.error shouldEqual 1
      err4.error shouldEqual 1
      err5.error shouldEqual 1
    }

    "Return error if the content type is not supported" in {
      val target = new VHttp(null, null)
      val err = target.req("", "", "XXX", "", Array.empty, 1000, response = false)
      err.error shouldEqual 2
    }

    "Return error if headers is not paired" in {
      val target = new VHttp(null, null)
      val err = target.req("", "", "application/json", "", Array("a", "b", "c"), 1000, response = false)
      err.error shouldEqual 3
    }

    "Return error if http request was failed is not paired" in {
      val httpProbe = TestProbe()
      val httpProxy = new TestHttpProxy(httpProbe.ref)

      val target = new VHttp(httpProxy, ActorMaterializer.create(system))

      val r = Future {
        target.req("POST", "https://google.com", "application/json", "xxx", Array("Session", "abc"), 1000, response = true)
      }

      val req = HttpRequest(method = HttpMethods.POST, uri = "https://google.com", entity = HttpEntity(ContentTypes.`application/json`, "xxx")).withHeaders(RawHeader("Session", "abc"))
      httpProbe.expectMsg(TestHttpProxy.Actions.SingleRequest(req))
      val p: Promise[HttpResponse] = Promise()
      httpProbe.reply(p.future)
      p.failure(new IllegalArgumentException("xxx"))
      val fr = Await.result(r, 1 second)
      fr.error shouldEqual 4
      fr.code shouldEqual 0
      fr.result shouldEqual "xxx"
    }
  }
}