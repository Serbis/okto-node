package ru.serbis.okto.node.proxy.system

import akka.actor.{ActorRef, ActorSystem, Props}
import akka.pattern.ask

import scala.concurrent.Await
import scala.concurrent.duration._

object TestActorSystemProxy {
  object Actions {
    case class ActorOf(props: Props)
  }

  object Predicts {
    case class Throw(ex: Throwable)
  }
}

class TestActorSystemProxy(tpRef: ActorRef, actorSystem: ActorSystem) extends ActorSystemProxy {
  import TestActorSystemProxy._

  override def actorOf(props: Props): ActorRef = {
    Await.result(tpRef.ask(Actions.ActorOf(props))(3 second), 3 second) match {
      case Predicts.Throw(ex) => throw ex
      case v: ActorRef => v
    }
  }

  override val system = actorSystem
}
