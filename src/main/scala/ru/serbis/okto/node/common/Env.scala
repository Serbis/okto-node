package ru.serbis.okto.node.common

import akka.actor.{ActorRef, ActorSystem}

case class Env(
  runtime: ActorRef = ActorRef.noSender,
  syscomsRep: ActorRef = ActorRef.noSender,
  usercomsRep: ActorRef = ActorRef.noSender,
  accessRep: ActorRef = ActorRef.noSender,
  serialBridge: ActorRef = ActorRef.noSender,
  rfBridge: ActorRef = ActorRef.noSender,
  nsdBridge: ActorRef = ActorRef.noSender,
  systemDaemon: ActorRef = ActorRef.noSender,
  vmPool: ActorRef = ActorRef.noSender,
  scriptsRep: ActorRef = ActorRef.noSender,
  storageRep: ActorRef = ActorRef.noSender,
  bootRep: ActorRef = ActorRef.noSender,
  eventer: ActorRef = ActorRef.noSender,
  system: Option[ActorSystem] = None
)
