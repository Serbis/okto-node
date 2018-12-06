package ru.serbis.okto.node.runtime.senv.vstorage

import akka.NotUsed
import akka.actor.{PoisonPill, Status}
import akka.pattern.ask
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream.{ActorMaterializer, IOResult}
import akka.util.{ByteString, Timeout}
import ru.serbis.okto.node.common.ReachTypes.ReachByteString
import ru.serbis.okto.node.log.Logger.LogEntryQualifier
import ru.serbis.okto.node.log.StreamLogger
import ru.serbis.okto.node.proxy.system.ActorSystemProxy
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}
import scala.util.Failure

/** Iterates the file as a stream of chunks. The form of formation of chunks is set by the heir of this class */
abstract class FileIterator[T](source: Source[ByteString, Future[IOResult]], chunkFlow: Flow[ByteString, ByteString, NotUsed], system: ActorSystemProxy, tm: Boolean = false) extends StreamLogger {

  setLogSourceName(s"FileIterator*${System.currentTimeMillis()}")
  setLogKeys(Seq("FileIterator"))

  implicit val logQualifier = LogEntryQualifier("static")
  implicit val askTimeout = if (tm) Timeout(0.4 second) else Timeout(4 second)
  implicit val mat = ActorMaterializer.create(system.system)

  val futTimeout = if (tm) 0.5 second else 5 second

  //File io controller
  val ioCtl = system.actorOf(IoCtl.props)

  //If controlled stream was failed
  var failed = false

  //Current cached chunk
  var chunk: Option[ByteString] = None


  //Complete stream with IoCtl actor as sink via chunk flow. Chunk flow defined in the upper class realization
  source via chunkFlow to Sink.actorRefWithAck(ioCtl, IoCtl.Internals.Init, IoCtl.Internals.Ack, IoCtl.Internals.Complete) run() map {
    case IOResult(_, Failure(e)) => ioCtl ! Status.Failure(e)
    case _ =>
  }

  //Load first chunk. If load was failed trow exception
  getNewChunk match {
    case Right(v) =>
      chunk = v
      logger.debug(s"Iterator initialized with start chunk '${v.get.toHexString}'")
    case Left(e) =>
      logger.error(s"Unable to initialize iterator because '${e.getMessage}'")
      throw e
  }


  /** Check if the stream has next chunk extraction */
  def hasNext() = {
    implicit val logQualifier = LogEntryQualifier("hasNext")

    val r = chunk.isDefined
    logger.debug(s"HasNext request competed with '$r'")
    r
  }

  /** Return next chunk. Heir must realize this method at part of conversion ByteString chunk to the T */
  def next(): T

  /** Internal next logic. Extract new chunk from the strem. This. method must be called from the next method realization */
  def _next() = {
    getNewChunk match {
      case Right(v) =>
        chunk = v
      case Left(e) =>
        chunk = None
        failed = true
    }
  }

  /** Stop controller and the upper stream. This method must be always called at the end of work with iterator. Failure to
    * compel with this rule will lead to memory leak in th application */
  def close() = {
    implicit val logQualifier = LogEntryQualifier("close")

    logger.info(s"Iterator closed")
    ioCtl ! PoisonPill
  }

  /** Receive new chunk frome the upper stream */
  private def getNewChunk:Either[Throwable, Option[ByteString]] = {
    implicit val logQualifier = LogEntryQualifier("getNewChunk")

    try {
      Await.result(ioCtl ? IoCtl.Commands.Pull, futTimeout) match {
        case IoCtl.Responses.Chunk(data) =>
          if (data.isDefined) {
            logger.debug(s"New chunk received with data '${data.get.toHexString}'")
            Right(Some(data.get))
          } else {
            logger.debug(s"New empty chunk received")
            Right(None)
          }

        case IoCtl.Responses.StreamFailed(e) =>
          logger.error(s"Unable to get new chunk because io controller was failed, reason '${e.getMessage}'")
          Left(e)

      }
    } catch {
      case e: Throwable =>
        logger.error(s"Unable to get new chunk because ask request to io controller was failed, reason '${e.getMessage}'")
        Left(e)
    }
  }
}
