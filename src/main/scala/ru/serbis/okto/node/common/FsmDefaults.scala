package ru.serbis.okto.node.common

import akka.actor.ActorRef

object FsmDefaults {
  trait Data { def originator: ActorRef = ActorRef.noSender }

  trait State

  case class Inputs[T](originator: ActorRef, request: T)

  trait InputsData[T] extends Data {
    def inputs: Inputs[T]
    override def originator = inputs.originator
  }
}