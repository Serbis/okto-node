package ru.serbis.okto.node.runtime

import akka.actor.{Actor, ActorRef, Props}
import akka.util.ByteString
import ru.serbis.okto.node.common.ReachTypes.ReachByteString
import ru.serbis.okto.node.log.Logger.LogEntryQualifier
import ru.serbis.okto.node.log.StreamLogger

import scala.collection.mutable

/** I / O flow of some command. Used to transmit / receive data to / from the outside world into the commands's
  * executive logic. In normal mode, this actor acts as a data buffer - it can write to or read from it any data.
  * It is possible to subscribe a certain actor to this stream. In this case, when you write, the data will not be
  * recorded in the buffer, but will be sent to all the subscribers available to the stream. */
object Stream {

  /** @param maxBufSize maximum internal buffer size in bytes
    * @param pid process identifier to which the thread belongs */
  def props(maxBufSize: Int, pid: Int) = Props(new Stream(maxBufSize, pid))

  object Commands {

    /** Read some data from internal buffer. Responds to the sender with a Data message with the data read. The answer
      * is immediate. If the buffer is empty, it will return an empty data object.
      *
      * @param size bytes count for reading
      */
    case class Read(size: Int)

    /** Returns the data to the requestor upon arrival into the stream. If the data buffer is empty when an incoming
      * message arrives, an entry is created for deferred data retrieval. When the data comes to the stream, they will
      * be sent to the applicant.  If at the time the message arrives, the buffer is not empty, there is an immediate
      * return of data from the buffer, even if their number is less than the number in the request. This mechanic does
      * not affect the sending of data to the subscribers of the stream, but it is similar to the Read message, only
      * in the deferred mode. Data is always returned in full.
      */
    case object ReadOnReceive

    /** Writes the data to the internal buffer. Responds to the sender with a message Written. If the size of the
      * current buffer size + data size is greater than the threshold set by maxBufSize, stream responds to the
      * sender with the message BufferIsFull
      *
      * @param data data to write
      */
    case class Write(data: ByteString)

    /** Writes the data to the internal buffer in the mode of ignoring the control characters. Responds to the sender
      * with a message Written. If the size of the current buffer size + data size is greater than the threshold set
      * by maxBufSize, stream responds to the sender with the message BufferIsFull.
      *
      * P. S. At the moment, the concatenation of control characters is at the idea stage. The message is used for
      * future compatibility.
      *
      * @param data data to write
      */
    case class WriteWrapped(data: ByteString)

    /** Subscribe the actor to receive data from the stream
      *
      * @param ref actor reference
      */
    case class Attach(ref: ActorRef)

    /** Flush internal buffer to the consumers. Respond with the Flushed message */
    case object Flush

    /** Unsubscribe the actor to receive data from the stream
      *
      * @param ref actor reference
      */
    case class Detach(ref: ActorRef)

    /** Request for getting stream pid. Respond with Pid message */
    case object GetPid

    /** Shut down the flow. If a thread has subscribers, each of them is sent a Closed message. After that, the actor
      * sends himself a Stop and answers the message to the sender of the original message */
    case object Close

    /** Performs a deferred closing stream. The command takes the stream to a special mode, in which, upon receiving
      * the Flush command, the data is flushed to subscribers, and the stream is subsequently closed. Prior to receiving
      * the Flush command, the stream is operating normally */
    case object FlushedClose
  }

  object Responses {
    /** Response for Read message */
    case class Data(bs: ByteString)

    /** Response for Write message */
    case object Written

    /** Response for Write message */
    case object BufferIsFull

    /** Response for Attach message */
    case object Attached

    /** Response for Detach message */
    case object Detached

    /** Response for GetPid message */
    case class Pid(value: Int)

    /** This message is a response to a Close request and is also sent to all subscribers in the event of a thread closing */
    case object Closed

    /** This message is a response to a Flush request */
    case object Flushed
  }
}

class Stream(maxBufSize: Int, pid: Int) extends Actor with StreamLogger {
  import Stream.Commands._
  import Stream.Responses._

  /** Internal data buffer */
  val buf = mutable.Queue.empty[Byte]

  /** Waiting deferred readers */
  val deferredReaders = mutable.Set.empty[ActorRef]

  /** Stream consumers */
  val consumers = mutable.HashSet.empty[ActorRef]

  var mustClosed = false

  setLogSourceName(s"Stream*${self.path.name}")
  setLogKeys(Seq("Stream"))

  implicit val logQualifier = LogEntryQualifier("static")

  override def receive = {

    /** See msg description */
    case Read(size) =>
      implicit val logQualifier = LogEntryQualifier("Read")
      val inSize = if (size > buf.size) buf.size else size
      val data = (1 to inSize).foldLeft(Vector.empty[Byte])((a, v) => a :+ buf.dequeue())
      val bs = ByteString(data.toArray)
      logger.debug(s"Read data from stream -> ${if (bs.size > 100) "CUTTED" else bs.toHexString}")
      sender() !  Data(bs)

    /** See msg description */
    case ReadOnReceive =>
      implicit val logQualifier = LogEntryQualifier("ReadOnReceive")
      if (buf.nonEmpty) {
        val inSize =  buf.size
        val data = (1 to inSize).foldLeft(Vector.empty[Byte])((a, v) => a :+ buf.dequeue())
        val bs = ByteString(data.toArray)
        logger.debug(s"Read buffered data from stream in deferred mode -> '${if (bs.size > 100) "CUTTED" else bs.toHexString}'")
        sender() !  Data(bs)
      } else {
        logger.debug(s"Created new deferred reader '${sender().path.name}'")
        deferredReaders += sender()
      }

    /** See msg description */
    case Write(data) =>
      implicit val logQualifier = LogEntryQualifier("Write")
      if (consumers.isEmpty) {
        if (buf.size + data.size <= maxBufSize) {
          if (deferredReaders.isEmpty) {
            buf ++= data.toArray
            logger.debug(s"Write to data buffer -> ${if (data.size > 100) "CUTTED" else data.toHexString}")
          } else {
            logger.debug(s"Write data to deferred readers -> ${if (data.size > 100) "CUTTED" else data.toHexString}")
            val bs = ByteString(data.toArray)
            deferredReaders.foreach(v => v ! Data(bs))
          }
          if (sender() != context.system.deadLetters) sender() ! Written
        } else {
          logger.debug(s"Attempt to write to a full buffer. Buffer size -> ${buf.size} / data size -> ${data.size}")
          if (sender() != context.system.deadLetters) sender() ! BufferIsFull
        }
      } else {
        logger.debug(s"Write data to consumers -> ${if (data.size > 100) "CUTTED" else data.toHexString}")
        consumers.foreach(a => {
          a ! Data(data)
        })
        if (deferredReaders.nonEmpty) {
          logger.debug(s"Write data to deferred requestors -> '${if (data.size > 100) "CUTTED" else data.toHexString}'")
          val bs = ByteString(data.toArray)
          deferredReaders.foreach(v => v ! Data(bs))
        }
        if (sender() != context.system.deadLetters) sender() ! Written
      }

      if (deferredReaders.nonEmpty) {
        logger.debug(s"Removed '${deferredReaders.size}' deferred reader")
        deferredReaders.clear()
      }

    /** See msg description */
    case WriteWrapped(data) =>
      implicit val logQualifier = LogEntryQualifier("WriteWrapped")
      //if (data == ByteString(80, 48, 23))
      //  println("x")

      if (consumers.isEmpty) {
        if (buf.size + data.size <= maxBufSize) {
          if (deferredReaders.isEmpty) {
            buf ++= data.toArray
            logger.debug(s"Write wrapped to data buffer -> '${if (data.size > 100) "CUTTED" else data.toHexString}'")
          } else {
            logger.debug(s"Write wrapped data to deferred readers -> ${if (data.size > 100) "CUTTED" else data.toHexString}")
            val bs = ByteString(data.toArray)
            deferredReaders.foreach(v => v ! Data(bs))
          }
          if (sender() != context.system.deadLetters) sender() ! Written
        } else {
          logger.debug(s"Attempt to write wrapped to a full buffer. Buffer size -> ${buf.size} / data size -> ${data.size}")
          if (sender() != context.system.deadLetters) sender() ! BufferIsFull
        }
      } else {
        logger.debug(s"Write wrapped data to consumers -> ${if (data.size > 100) "CUTTED" else data.toHexString}")
        consumers.foreach(a => {
          a ! Data(data)
        })
        if (deferredReaders.nonEmpty) {
          logger.debug(s"Write wrapped data to deferred requestors -> ${if (data.size > 100) "CUTTED" else data.toHexString}")
          val bs = ByteString(data.toArray)
          deferredReaders.foreach(v => v ! Data(bs))
        }
        if (sender() != context.system.deadLetters) sender() ! Written
      }

      if (deferredReaders.nonEmpty) {
        logger.debug(s"Removed '${deferredReaders.size}' deferred reader")
        deferredReaders.clear()
      }

    /** See msg description */
    case Attach(ref) =>
      implicit val logQualifier = LogEntryQualifier("Attach")
      consumers += ref
      logger.debug(s"Attached consumer -> ${ref.path}")
      if (sender() != context.system.deadLetters) sender() ! Attached

    /** See msg description */
    case Detach(ref) =>
      implicit val logQualifier = LogEntryQualifier("Detach")
      consumers -= ref
      logger.debug(s"Detached consumer -> ${ref.path}")
      sender() ! Detached

    /** See msg description */
    case GetPid => sender() ! Pid(pid)

    /** See msg description */
    case Close =>
      implicit val logQualifier = LogEntryQualifier("Close")
      //TODO тест этого куска кода
      if (consumers.nonEmpty && buf.nonEmpty) {
        logger.debug(s"Flushed '${buf.size}' bytes from buffer to consumers before stop stream")
        val data = ByteString((1 to buf.size).foldLeft(Vector.empty[Byte])((x, _) => x :+ buf.dequeue()).toArray)
        consumers.foreach(a => {
          a ! Data(data)
        })
      }
      consumers.foreach(v => v ! Closed)
      if (sender() != context.system.deadLetters) sender() ! Closed
      logger.debug(s"Stream closed")
      context.stop(self)

    /** See msg description */
    case FlushedClose =>
      logger.debug("Stream planned to flushed close")
      if (buf.isEmpty)
        self ! Close
      else
        mustClosed = true

    /** See msg description */
    case Flush =>
      if (consumers.nonEmpty && buf.nonEmpty) {
        logger.debug(s"Flushed '${buf.size}' bytes from buffer to consumers")
        val data = ByteString((1 to buf.size).foldLeft(Vector.empty[Byte])((x, _) => x :+ buf.dequeue()).toArray)
        consumers.foreach(a => {
          a ! Data(data)
        })
        if (mustClosed)
          self ! Close
      } else {
        logger.debug(s"Nothing to flush, buffer is empty or no consumers")
      }

      if (sender() != context.system.deadLetters) sender() ! Flushed
  }
}