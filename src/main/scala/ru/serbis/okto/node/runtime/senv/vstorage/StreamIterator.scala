package ru.serbis.okto.node.runtime.senv.vstorage

import akka.NotUsed
import akka.stream.IOResult
import akka.stream.scaladsl.{Flow, Source}
import akka.util.ByteString
import ru.serbis.okto.node.log.StreamLogger
import ru.serbis.okto.node.proxy.system.ActorSystemProxy
import scala.concurrent.Future

/** Iterate file as fragments of predefined size */
object StreamIterator {

  //Flat chunk flow that do nothing
  val chunkFlow = Flow[ByteString]

  def apply(source: Source[ByteString, Future[IOResult]], system: ActorSystemProxy, tm: Boolean = false): StreamIterator = new StreamIterator(source, chunkFlow, system, tm)
}

class StreamIterator(source: Source[ByteString, Future[IOResult]], chunkFlow: Flow[ByteString, ByteString, NotUsed], system: ActorSystemProxy, tm: Boolean = false)
  extends FileIterator[String](source, chunkFlow, system, tm) with StreamLogger {

  setLogSourceName(s"StreamIterator*${System.currentTimeMillis()}")
  setLogKeys(Seq("StreamIterator"))

  /** Convert chunk to the urt8 string */
  override def next() = {
    val result = if (chunk.isDefined) chunk.get.utf8String else null
    if (chunk.isDefined) _next()
    result
  }
}
