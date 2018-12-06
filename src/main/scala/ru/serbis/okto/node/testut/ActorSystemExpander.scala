package ru.serbis.okto.node.testut

import akka.actor.{ActorRef, ActorSystem, Props}

trait ActorSystemExpander {
  def actorOf(props: Props): ActorRef
}

class RealActorSystem extends ActorSystemExpander {
  var system: ActorSystem = _

  override def actorOf(props: Props): ActorRef = system.actorOf(props)
}

class TestActorSystem(f: PartialFunction[Props, ActorRef]) extends ActorSystemExpander {
  override def actorOf(props: Props): ActorRef = f(props)
}
