package ru.serbis.okto.node.proxy.http

import akka.actor.ActorSystem
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.{HttpRequest, HttpResponse}

import scala.concurrent.Future

class RealHttpProxy(implicit system: ActorSystem) extends HttpProxy {
  override def singleRequest(request: HttpRequest): Future[HttpResponse] = Http().singleRequest(request)
}
