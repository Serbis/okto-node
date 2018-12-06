package ru.serbis.okto.node.common

import akka.actor.{ActorRef, ActorSystem}

case class Env(
  runtime: ActorRef = ActorRef.noSender,
  syscomsRep: ActorRef = ActorRef.noSender,
  usercomsRep: ActorRef = ActorRef.noSender,
  serialBridge: ActorRef = ActorRef.noSender,
  systemDaemon: ActorRef = ActorRef.noSender,
  vmPool: ActorRef = ActorRef.noSender,
  scriptsRep: ActorRef = ActorRef.noSender,
  storageRep: ActorRef = ActorRef.noSender,
  system: Option[ActorSystem] = None
)
