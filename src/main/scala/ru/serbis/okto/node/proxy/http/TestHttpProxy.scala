package ru.serbis.okto.node.proxy.http

import akka.actor.ActorRef
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.pattern.ask

import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

object TestHttpProxy {
  object Actions {
    case class SingleRequest(request: HttpRequest)
  }

  object Predicts {
    case class Throw(ex: Throwable)
  }
}

class TestHttpProxy(tpRef: ActorRef) extends HttpProxy {
  import TestHttpProxy._

  override def singleRequest(request: HttpRequest): Future[HttpResponse] = {
    Await.result(tpRef.ask(Actions.SingleRequest(request))(3 second), 3 second) match {
      case Predicts.Throw(ex) => throw ex
      case v: Future[HttpResponse] => v
    }
  }
}
