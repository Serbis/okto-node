package ru.serbis.okto.node.proxy.system

import akka.actor.{ActorRef, ActorSystem, Props}

class RealActorSystemProxy(actorSystem: ActorSystem) extends ActorSystemProxy {
  override def actorOf(props: Props, name: Option[String] = None): ActorRef = {
    if (name.isDefined) system.actorOf(props, name.get)
    else system.actorOf(props)
  }

  override val system = actorSystem
}
