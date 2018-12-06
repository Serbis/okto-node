package ru.serbis.okto.node.proxy.system

import akka.actor.{ActorRef, ActorSystem, Props}

/** Proxy for akka.actor.ActorSystem. This system is used to create end-to-end testing of AS operations at the unit test
  * level. The principles of this proxy in the following. This trait has two implementations - test and real. Real
  * implementation duplicates the corresponding calls from the ActorSystem object. The test implementation is used for testing
  * to intercept method calls and works as follows. The test implementation constructor has the reference of some actor that
  * will intercept requests, usually TestProbe. When calling any target method, the code of this method makes the ask call
  * to this actor by passing the action case class that defines the given method and its parameters. The interceptor actor
  * checks the received message for correctness, and then responds with a result that should return the target method. At
  * the same time, the interceptor can respond with a special message Throw, upon receipt of which the proxy initiates an
  * throw of the exception specified in the body message. All that remains to be done in practice is to transfer the
  * necessary implementation of the proxy to the actor who is going to use the actor system methods. */
trait ActorSystemProxy {
  val system: ActorSystem
  def actorOf(props: Props): ActorRef
}
