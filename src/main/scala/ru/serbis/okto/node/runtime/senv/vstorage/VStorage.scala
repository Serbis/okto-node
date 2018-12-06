package ru.serbis.okto.node.runtime.senv.vstorage

import akka.actor.ActorRef
import akka.pattern.ask
import akka.util.{ByteString, Timeout}
import ru.serbis.okto.node.log.Logger.LogEntryQualifier
import ru.serbis.okto.node.log.StreamLogger
import ru.serbis.okto.node.proxy.system.ActorSystemProxy
import ru.serbis.okto.node.reps.StorageRep.Commands._
import ru.serbis.okto.node.reps.StorageRep.Responses.{BlobData, OperationError, OperationSuccess, StreamData}
import scala.concurrent.Await
import scala.concurrent.duration._

/** Storage functions
  *
  * @param storage storage repsotory ref
  * @param system actor system proxy
  * @param tm test mode flag
  */
class VStorage(storage: ActorRef, system: ActorSystemProxy, tm: Boolean = false) extends StreamLogger {

  setLogSourceName(s"VStorage*${System.currentTimeMillis()}")
  setLogKeys(Seq("VStorage"))

  implicit val logQualifier = LogEntryQualifier("static")
  implicit val askTimeout = if (tm) Timeout(0.4 second) else Timeout(4 second)

  val futTimeout = if (tm) 0.5 second else 5 second


  /** Write string to the file. At this operation all content of the target file will be rewrite.
    *
    * @param file file name
    * @param data string to write
    * @return true if operation was success ot false if not
    */
  def write(file: String, data: String): Boolean = {
    implicit val logQualifier = LogEntryQualifier("write")
    inWrap(ReWrite(file, ByteString(data)), false) {
      case OperationSuccess =>
        logger.debug(s"File '$file' was rewrite")
        true
      case OperationError(reason) =>
        logger.error(s"Error occurred at attempt to rewrite the file '$file', reason '$reason'")
        false
    }
  }

  /** Append file with specified string
    *
    * @param file file name
    * @param data string to write
    * @return true if operation was success ot false if not
    */
  def append(file: String, data: String): Boolean = {
    implicit val logQualifier = LogEntryQualifier("append")
    inWrap(Append(file, ByteString(data)), false) {
      case OperationSuccess =>
        logger.debug(s"File '$file' was appended with content '$data'")
        true
      case OperationError(reason) =>
        logger.error(s"Error occurred at attempt to append the file '$file' with content '$data', reason '$reason'")
        false
    }
  }

  /** Append specified file
    *
    * @param file file name
    * @return true if operation was success ot false if not
    */
  def delete(file: String): Boolean = {
    implicit val logQualifier = LogEntryQualifier("delete")
    inWrap(Delete(file), false) {
      case OperationSuccess =>
        logger.debug(s"File '$file' was deleted")
        true
      case OperationError(reason) =>
        logger.error(s"Error occurred at attempt to delete the file '$file', reason '$reason'")
        false
    }
  }

  /** Read all data from the file as string
    *
    * @param file file name
    * @return file content as string or null if some error was occurred
    */
  def read(file: String): String = {
    implicit val logQualifier = LogEntryQualifier("read")
    inWrap(Read(file), null) {
      case BlobData(data) =>
        logger.debug(s"File '$file' was read with data '${data.utf8String}'")
        data.utf8String
      case OperationError(reason) =>
        logger.error(s"Error occurred at attempt to read the file '$file', reason '$reason'")
        null
    }
  }

  /** Read file as stream of string chunks
    *
    * @param file file name
    * @param chunkSize chunk size
    * @return iterator that iterate through a file with text elements
    */
  def readStream(file: String, chunkSize: Int): StreamIterator = {
    implicit val logQualifier = LogEntryQualifier("readStream")
    inWrap(ReadAsStream(file, chunkSize), null) {
      case StreamData(stream) =>
        logger.debug(s"File '$file' was read as stream with chunk size '$chunkSize'")
        try {
          StreamIterator(stream, system)
        } catch  {
          case e: Throwable =>
            null
        }
      case OperationError(reason) =>
        logger.error(s"Error occurred at attempt to read the file '$file' as stream, reason '$reason'")
        null
    }
  }

  /** Read file as stream of text block separated by separator string
    *
    * @param file file name
    * @param separator char for separation
    * @return iterator that iterate through a file with text elements
    */
  def readStreamSep(file: String, separator: String): StreamSeparatedIterator = {
    implicit val logQualifier = LogEntryQualifier("readStream")
    inWrap(ReadAsStream(file, 1024), null) {
      case StreamData(stream) =>
        logger.debug(s"File '$file' was read as separated stream with separator '$separator'")
        StreamSeparatedIterator(stream, separator, system)
      case OperationError(reason) =>
        logger.error(s"Error occurred at attempt to read the file '$file' as separated stream, reason '$reason'")
        null
    }
  }

  /** Wrapper for requests to the storage. Ask storage actor, wait result and apply partial function to it. If storage does
    * not respond, complete function with null */
  private def inWrap[T](req: Any, tr: Any)(f: PartialFunction[Any, T]): T = {
    val b: PartialFunction[Any, Any] = {
      case m: Any =>
        logger.warning(s"Received unexpected response from storage repository '$m''")
        tr
    }

    def p = f orElse b

    try {
      p(Await.result(storage ? req, futTimeout)).asInstanceOf[T]
    } catch {
      case _: Throwable =>
        logger.warning(s"Unable receive response from storage repository - response timeout'")
        null.asInstanceOf[T]
    }
  }
}
