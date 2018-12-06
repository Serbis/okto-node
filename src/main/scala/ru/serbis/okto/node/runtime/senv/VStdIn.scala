package ru.serbis.okto.node.runtime.senv

import akka.actor.ActorRef
import akka.util.ByteString
import ru.serbis.okto.node.log.Logger.LogEntryQualifier
import ru.serbis.okto.node.log.StreamLogger
import ru.serbis.okto.node.runtime.{Stream, StreamControls}

import scala.annotation.tailrec
import scala.concurrent.Await
import scala.concurrent.duration._
import scala.concurrent.ExecutionContext.Implicits.global
import akka.pattern.ask

import scala.collection.mutable

class VStdIn(executor: ActorRef, stdIn: ActorRef) extends StreamLogger {

  setLogSourceName(s"VStdIn*${System.currentTimeMillis()}")
  setLogKeys(Seq("VStdIn"))

  implicit val logQualifier = LogEntryQualifier("static")

  val stdInBuf = mutable.Queue.empty[Byte]

  def read(timeout: Int): String = {
    implicit val logQualifier = LogEntryQualifier("read")

    @tailrec
    def rec(deadline: Long): String = {
      val r = try {
        Await.result(stdIn.ask(Stream.Commands.ReadOnReceive)(timeout millis), timeout + 1000 millis) match {
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
}