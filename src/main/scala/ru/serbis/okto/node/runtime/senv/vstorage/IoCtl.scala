package ru.serbis.okto.node.runtime.senv.vstorage

import akka.actor.{Actor, ActorRef, PoisonPill, Props, Status}
import akka.util.ByteString
import ru.serbis.okto.node.log.Logger.LogEntryQualifier
import ru.serbis.okto.node.log.StreamLogger
import ru.serbis.okto.node.common.ReachTypes.ReachByteString

/** Controls I / O flow. Allow you to select an element from the stream in non-blocking mode and handle any errors that
  * occur */
object IoCtl {
  def props = Props(new IoCtl)

  object Commands {

    /** Get new element from stream. Respond with message Chunk. Chunk may contain ByteString if data if presented or None if no
      * more data. Alse command may respond with StreamFailure if stream was break with exception */
    case object Pull
  }

  object Responses {

    /** Chunk with data or None if stream is empty. If stream is empty, after respond with this message, actor self terminates */
    case class Chunk(data: Option[ByteString])

    /** Respond for Pull command if stream was closed by error */
    case class StreamFailed(reason: Throwable)
  }

  object Internals {

    /** n/c */
    case object Init

    /** n/c */
    case object Ack

    /** n/c */
    case object Complete
  }
}

class IoCtl extends Actor with StreamLogger {
  import IoCtl.Internals._
  import IoCtl.Commands._
  import IoCtl.Responses._

  setLogSourceName(s"IoCtl*${System.currentTimeMillis()}")
  setLogKeys(Seq("IoCtl"))

  implicit val logQualifier = LogEntryQualifier("static")

  /** Current chunk */
  var chunk: Option[ByteString] = None

  /** Current pull requestor */
  var requestor: Option[ActorRef] = None

  /** If this flag set to true, it indicates that no more data and thread. Pull request received under this flag, respond
    * with Chunk(None) and terminate actor */
  var finished = false

  /** Stream actor ref (for Ack)*/
  var streamRef = ActorRef.noSender

  /** Failure reason container */
  var failed: Option[Throwable] = None

  logger.debug("File IO controller was started")

  override def receive = {

    /** Stream init message */
    case Init =>
      implicit val logQualifier = LogEntryQualifier("Init")

      logger.debug("File IO controller was initialized by stream")
      sender() ! Ack

      /** Data from the stream */
    case data: ByteString =>
      implicit val logQualifier = LogEntryQualifier("Data")

      if (requestor.isDefined) {
        logger.debug(s"Complete previously received Pull request with data '${data.toHexString}'")
        requestor.get ! Chunk(Some(data))
        requestor = None
        sender() ! Ack
      } else {
        logger.debug(s"Pull request is not defined, cache chunk with data '${data.toHexString}'")
        chunk = Some(data)
        streamRef = sender()
      }

    /** Stream complete message */
    case Complete =>
      implicit val logQualifier = LogEntryQualifier("Complete")

      finished = true
      if (chunk.isEmpty) {
        if (requestor.isDefined) {
          logger.debug(s"Controller marked as finished. To cached requestor sent empty chunk")
          requestor.get ! Chunk(None)
        } else {
          logger.debug(s"Controller marked as finished")
        }

        //self ! PoisonPill
      } else {
        logger.debug(s"Controller marked as finished")
      }

    /** Stream failure message */
    case Status.Failure(e) =>
      implicit val logQualifier = LogEntryQualifier("Failure")

      if (requestor.isDefined) {
        logger.warning(s"Controlled stream is failed. Send StreamFailed to defined Pull requestor and terminate controller")
        requestor.get ! StreamFailed(e)
        self ! PoisonPill
      } else {
        logger.warning(s"Controlled stream is failed. Terminate controller")
        failed = Some(e)
      }

    /** See description of the message */
    case Pull =>
      implicit val logQualifier = LogEntryQualifier("Pull")

      if (failed.isDefined) {
        logger.warning(s"Controller marked as failed. Respond with StreamFailed message")
        sender() ! StreamFailed(failed.get)
      } else {
        if (finished) {
          if (chunk.isDefined) {
            logger.debug(s"Controller is finished. Respond with cached chunk with data '${chunk.get.toHexString}'")
            sender() ! Chunk(Some(chunk.get))
            chunk = None
          } else {
            logger.debug(s"Controller is finished. Respond with empty chunk and terminate controller")
            sender() ! Chunk(None)
            //self ! PoisonPill
          }
        } else {
          if (chunk.isDefined) {
            logger.debug(s"Respond with cached chunk with data '${chunk.get.toHexString}'")
            sender() ! Chunk(Some(chunk.get))
            chunk = None
            streamRef ! Ack
          } else {
            logger.debug(s"Chunk not yet received, request cached")
            requestor = Some(sender())
          }
        }
      }
  }
}
