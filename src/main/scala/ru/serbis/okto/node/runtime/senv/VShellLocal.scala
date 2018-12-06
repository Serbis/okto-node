package ru.serbis.okto.node.runtime.senv

import akka.actor.ActorRef
import akka.util.ByteString
import ru.serbis.okto.node.log.Logger.LogEntryQualifier
import ru.serbis.okto.node.log.StreamLogger
import ru.serbis.okto.node.runtime.{Stream, StreamControls}
import scala.annotation.tailrec
import scala.collection.mutable
import scala.concurrent.Await
import scala.concurrent.duration._
import akka.pattern.ask

class VShellLocal(override val process: ActorRef,
                  override val stdIn: ActorRef,
                  override val stdOut: ActorRef) extends VShell(process, stdIn, stdOut) with StreamLogger {

  setLogSourceName(s"VShellLocal*${System.currentTimeMillis()}")
  setLogKeys(Seq("VShellLocal"))

  implicit val logQualifier = LogEntryQualifier("static")

  val stdInBuf = mutable.Queue.empty[Byte]

  /** It pushes the output from the standard output of the sympole PROMPT. If this character is received during the
    * timeout, it returns true. Otherwise, false is returned. If after the symbol PROMPT some data goes, they are
    * stored in the internal buffer. All data going before PROMPT is discarded.
    *
    * @param timeout expectation timeout
    * @return true if prompt was received or false if not
    */
  override def expectPrompt(timeout: Int): Boolean = {
    implicit val logQualifier = LogEntryQualifier("expectPrompt")
    val bPos = stdInBuf.indexOf(StreamControls.PROMPT)
      if (bPos != -1) { //TODO протестировать этот участок кода
        //Сброс буфера до PROMPT
        true
      } else {
        try {
          Await.result(stdOut.ask(Stream.Commands.ReadOnReceive)(timeout millis), timeout + 1000 millis) match {
            case Stream.Responses.Data(data) =>
              val pPos = data.indexOf(StreamControls.PROMPT)
              val after = data.slice(pPos + 1, data.size)
              after.foreach(v => stdInBuf.enqueue(v))
              if (pPos != -1) {
                true
              } else {
                false
              }

            case m =>
              logger.warning(s"While prompt expectation received some unexpected message '$m'")
              false
          }
        } catch {
          case _: Throwable =>
            logger.debug("Prompt expectation timeout was reached")
            false
        }
      }
    }


  override def expectEof(timeout: Int): Boolean = {
    implicit val logQualifier = LogEntryQualifier("expectEof")
    val bPos = stdInBuf.indexOf(StreamControls.EOF)
    if (bPos != -1) { //TODO протестировать этот участок кода
      //Сброс буфера до EOF
      true
    } else {
      try {
        Await.result(stdOut.ask(Stream.Commands.ReadOnReceive)(timeout millis), timeout + 1000 millis) match {
          case Stream.Responses.Data(data) =>
            val pPos = data.indexOf(StreamControls.EOF)
            val after = data.slice(pPos + 1, data.size)
            after.foreach(v => stdInBuf.enqueue(v))
            if (pPos != -1) {
              true
            } else {
              false
            }

          case m =>
            logger.warning(s"While eof expectation received some unexpected message '$m'")
            false
        }
      } catch {
        case _: Throwable =>
          logger.debug("Eof expectation timeout was reached")
          false
      }
    }

  }

  /** Writes a string to standard output, appending the EOI character to the end.
    *
    * @param str input string
    */
  override def write(str: String): Boolean = {
    val data = ByteString(str) ++ ByteString(Array(StreamControls.EOI))
    try {
      Await.result(stdIn.ask(Stream.Commands.Write(data))(3 second), 4 second) match {
        case Stream.Responses.Written =>
          logger.debug(s"Write '${str.length}' bytes to output stream")
          true
        case Stream.Responses.BufferIsFull =>
          logger.warning(s"Unable to write '${str.length}' bytes to output stream because it is overflow")
          false
        case m =>
          logger.warning(s"While output stream writing received some unexpected message '$m'")
          false
      }
    } catch {
      case _: Throwable =>
        logger.debug("Stream write timeout reached")
        false
    }
  }

  override def read(timeout: Int): String = {
    implicit val logQualifier = LogEntryQualifier("expectPrompt")

    @tailrec
    def rec(deadline: Long): String = {
      val r = try {
        Await.result(stdOut.ask(Stream.Commands.ReadOnReceive)(timeout millis), timeout + 1000 millis) match {
          case Stream.Responses.Data(data) =>
            val pPos = data.indexOf(StreamControls.EOI)
            if (pPos != -1) {
              val split = data.slice(0, pPos)
              val after = data.slice(pPos + 1, data.size)
              val buffered = ByteString((1 to stdInBuf.size).foldLeft(Vector.empty[Byte])((a, v) => a :+ stdInBuf.dequeue()).toArray)
              if (after.nonEmpty)
                after.foreach(v => stdInBuf.enqueue(v))
              (false, (buffered ++ split).utf8String)
            } else {
              data.foreach(v => stdInBuf.enqueue(v))
              (true, null)
            }

          case m =>
            //logger.warning(s"Unable to create new local shell because : 'Unexpected message from executor '$m''")
            (false, null)
        }
      } catch {
        case e: Throwable =>
          /*logger.warning(s"Unable to create new local shell because : '${e.getMessage}'")*/
          (false, null)
      }

      if (r._1) {
        rec(deadline)
      } else {
        r._2
      }
    }

    rec(System.currentTimeMillis() + timeout)
  }

  override def close(): Unit = {

  }
}