package ru.serbis.okto.node.events

import akka.actor.{Actor, ActorRef, Props}
import ru.serbis.okto.node.log.Logger.LogEntryQualifier
import ru.serbis.okto.node.log.StreamLogger

//FIXME this actor is a bottleneck, because count of an events may be a very big. This actor must be refactored to the router

/**
  * This actor realize events roting to the followers. Events may by an any subclass of the SystemEvent class. As follower
  * may be an any standalone actor
  */
object Eventer {

  /** n/c */
  def props(): Props = Props(new Eventer)

  object Commands {

    /** Receive some event and transmit in to the followers
      *
      * @param event event for followers
      */
    case class Receive(event: SystemEvent)

    /** Subscribe some actor for receive some events
      *
      * @param eid expected events with the specified identifiers. If it is None, subscriber will be subscribed as
      *            watcher. It will be receive all types of events
      * @param ref subscriber
      */
    case class Subscribe(eid: Option[Int], ref: ActorRef)

    /** Unsubscribe actor from events
      *
      * @param eid event identifier, if it is None, actor will by unsubscribed from all events
      * @param ref subscriber
      */
    case class Unsubscribe(eid: Option[Int], ref: ActorRef)
  }

  object Responses {

    /** Event was delivered to the all followers */
    case class Delivered(event: SystemEvent)

    /** Event not delivered by reasons specified in the appropriate field */
    case class NotDelivered(event: SystemEvent, reason: Int)

    /** Response for subscribe command */
    case object Subscribed

    /** Response for unsubscribe command */
    case object Unsubscribed
  }
}

class Eventer extends Actor with StreamLogger {
  import Eventer.Commands._
  import Eventer.Responses._

  setLogSourceName(s"Eventer")
  setLogKeys(Seq("Eventer"))
  implicit val logQualifier = LogEntryQualifier("static")

  /** Watchers for all events */
  var watchers = Set.empty[ActorRef]

  /** Followers for some eid */
  var followers = Map.empty[Int, Set[ActorRef]]

  logger.info("Events router was started")

  override def receive = {

    /** See the message description */
    case Receive(event) =>
      implicit val logQualifier = LogEntryQualifier("Receive")

      watchers.foreach(r => r ! event) //Send to all watchers

      val folls = followers.get(event.eid)
      if (folls.isDefined)
        folls.get.foreach(r => r ! event)

      logger.debug(s"Received new event[event=$event}]")

      sender() ! Delivered(event)

    /** See the message description */
    case Subscribe(eid, ref) =>
      implicit val logQualifier = LogEntryQualifier("Subscribe")

      if (eid.isDefined) {
        val folls = followers.get(eid.get)
        if (folls.isDefined)
          followers = followers + (eid.get -> (folls.get + ref))
        else
          followers = followers + (eid.get -> Set(ref))

        logger.debug(s"Actor was subscribed for event [eid=$eid, ref=${ref.path.name.toString}]")
      } else {
        logger.debug(s"Actor was subscribed as watcher [ref=${ref.path.name.toString}]")
        watchers = watchers + ref
      }

      sender() ! Subscribed

    /** See the message description */
    case Unsubscribe(eid, ref) =>
      implicit val logQualifier = LogEntryQualifier("Unsubscribe")

      if (eid.isDefined) {
        val folls = followers.get(eid.get)
        if (folls.isDefined)
          followers = followers + (eid.get -> (folls.get - ref))

        logger.debug(s"Actor was unsubscribed from event [eid=$eid, ref=${ref.path.name.toString}]")
      } else {
        watchers = watchers - ref
        followers = followers.map(v => {
          v._1 -> v._2.filter(p => p != ref)
        }).filter(v => v._2.nonEmpty)

        logger.debug(s"Actor was unsubscribed from all events [ref=${ref.path.name.toString}]")
      }


      sender() ! Unsubscribed
  }
}