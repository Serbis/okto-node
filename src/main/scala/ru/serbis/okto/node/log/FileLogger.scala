package ru.serbis.okto.node.log

import java.io.File
import java.nio.file.{Files, Path, StandardOpenOption}
import java.util.Date

import akka.actor.{Actor, Props}
import akka.util.ByteString
import ru.serbis.okto.node.log.Logger._
import ru.serbis.okto.node.streams.RouterSink2.Ack

import scala.util.Try

object FileLogger {
  def props(path: Path, truncate: Boolean) = Props(new FileLogger(path, truncate))
}

class FileLogger(path: Path, truncate: Boolean) extends Actor {

  val defPath = new File("/tmp/node.log").toPath
  val dateFormat = new java.text.SimpleDateFormat("dd-MM-yyyy HH:mm:ss")

  Try {
    if (truncate)
      Files.deleteIfExists(path)

    if (Files.notExists(path))
      Files.createFile(path)
  } recover {
    case _ =>
      if (truncate)
        Files.deleteIfExists(defPath)

      if (Files.notExists(defPath))
        Files.createFile(defPath)
  }

  override def receive = {
    case t: LogMessage =>
      Files.write(path, ByteString(format(t)).toArray, StandardOpenOption.APPEND)
      sender() ! Ack()
  }

  def format(logMessage: LogMessage) = logMessage match {
    case t: FatalMessage =>  s"FATAL/${t.from.name}*${t.qualifier.name}/${dateFormat.format(new Date())} ---> ${t.msg}\n"
    case t: ErrorMessage =>  s"ERROR/${t.from.name}*${t.qualifier.name}/${dateFormat.format(new Date())} ---> ${t.msg}\n"
    case t: DebugMessage => s"DEBUG/${t.from.name}*${t.qualifier.name}/${dateFormat.format(new Date())} ---> ${t.msg}\n"
    case t: WarningMessage => s"WARNING/${t.from.name}*${t.qualifier.name}/${dateFormat.format(new Date())} ---> ${t.msg}\n"
    case t: InfoMessage => s"INFO/${t.from.name}*${t.qualifier.name}/${dateFormat.format(new Date())} ---> ${t.msg}\n"
  }
}
