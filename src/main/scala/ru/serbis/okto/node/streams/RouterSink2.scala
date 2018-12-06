package ru.serbis.okto.node.streams

import akka.actor.{ActorRef, ActorSystem}
import akka.routing.{Router, RoutingLogic}
import akka.stream.stage.{GraphStage, GraphStageLogic, InHandler}
import akka.stream.{Attributes, Inlet, SinkShape}
import ru.serbis.okto.node.streams.RouterSink2.DeliveryStrategy


/*

Стратегии:
1. LoadThreshold как в RouterSink
2. BradcastDelivery пока все цели не ответят, новое сообщение не будет обрабатываться

 */
object RouterSink2 {
  trait ControlMessage
  /** Add new destination */
  case class AddDestination(ref: ActorRef) extends ControlMessage
  /** Remove destination */
  case class RemoveDestination(ref: ActorRef) extends ControlMessage
  /** The response message that each target actor should send after processing
    *  the message for realize backpressure mechanism */
  case class Ack()


  trait DeliveryStrategy

  /** Next message will be pulled only after all routees respond Ack message.
    * This stratage works only for BroadcastRouterLogic. */
  case class BroadcastStrategy() extends DeliveryStrategy

  case class LoadThresholdStrategy(loadFactor: Int) extends DeliveryStrategy
}

class RouterSink2(system: ActorSystem, strategy: DeliveryStrategy, routerLogic: RoutingLogic) extends GraphStage[SinkShape[Any]] {
  import RouterSink2._

  val in: Inlet[Any] = Inlet("Inlet")

  override def shape: SinkShape[Any] = SinkShape(in)

  override def createLogic(inheritedAttributes: Attributes) = new GraphStageLogic(shape) {
    var inActor: ActorRef = _
    var load = 0
    var inPulled = true

    var router = {
      val routees = Vector.empty
      Router(routerLogic, routees)
    }

    override def preStart(): Unit = {
      inActor = getStageActor((cort) => {
        cort._2 match {
          case Ack() =>
            strategy match {
              case BroadcastStrategy() =>
                load -= 1
                if (load <= 0) pull(in)
              case LoadThresholdStrategy(_) =>
                //println("-----------------------B")
                load -= 1
                if(!inPulled) {
                  pull(in)
                  inPulled = true
                }
            }
        }
      }).ref
      pull(in)
    }

    override def postStop(): Unit =
      super.postStop()

    setHandler(in, new InHandler {
      override def onPush(): Unit = {
        inPulled = false
        grab(in) match {
          case AddDestination(ref) =>
            router = router.addRoutee(ref)
            pull(in)
          case RemoveDestination(ref) =>
            router = router.removeRoutee(ref)
            pull(in)
          case t: Any =>
            strategy match {
              case BroadcastStrategy() =>
                load = router.routees.size
                router.route(t, inActor)
              case LoadThresholdStrategy(loadFactor) =>
                //println("-----------------------A")
                load += 1
                router.route(t, inActor)
                if (load < loadFactor) {
                  pull(in)
                  inPulled = true
                }
            }
        }
      }
    })
  }
}
