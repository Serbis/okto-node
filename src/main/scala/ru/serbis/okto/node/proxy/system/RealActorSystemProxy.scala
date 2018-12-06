package ru.serbis.okto.node.proxy.system

import akka.actor.{ActorRef, ActorSystem, Props}

class RealActorSystemProxy(actorSystem: ActorSystem) extends ActorSystemProxy {
  override def actorOf(props: Props): ActorRef = system.actorOf(props)

  override val system = actorSystem
}
