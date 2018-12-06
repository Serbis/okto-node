package ru.serbis.okto.node.runtime.senv.vstorage

import akka.NotUsed
import akka.stream.IOResult
import akka.stream.scaladsl.{Flow, Framing, Source}
import akka.util.ByteString
import ru.serbis.okto.node.log.StreamLogger
import ru.serbis.okto.node.proxy.system.ActorSystemProxy
import scala.concurrent.Future

/** Iterate file as fragments separated by delimiter */
object StreamSeparatedIterator {

  //Delimiter chunk logic. Split input data by the delimiter and format new chunks without delimiters
  def chunkFlow(splitter: String) = Framing.delimiter(ByteString(splitter), Int.MaxValue, allowTruncation = true)

  def apply(source: Source[ByteString, Future[IOResult]], splitter: String, system: ActorSystemProxy, tm: Boolean = false): StreamSeparatedIterator =
    new StreamSeparatedIterator(source, chunkFlow(splitter), system, tm)
}

class StreamSeparatedIterator(source: Source[ByteString, Future[IOResult]], chunkFlow: Flow[ByteString, ByteString, NotUsed], system: ActorSystemProxy, tm: Boolean = false)
  extends FileIterator[String](source, chunkFlow, system, tm) with StreamLogger {

  setLogSourceName(s"StreamSeparatedIterator*${System.currentTimeMillis()}")
  setLogKeys(Seq("StreamSeparatedIterator"))

  /** Convert chunk to the urt8 string */
  override def next() = {
    val result = if (chunk.isDefined) chunk.get.utf8String else null
    if (chunk.isDefined) _next()
    result
  }
}
