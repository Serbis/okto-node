package ru.serbis.okto.node.proxy.files

import java.io.{File, FileInputStream, FileOutputStream, RandomAccessFile}
import java.nio.file.attribute.FileAttribute
import java.nio.file.{CopyOption, LinkOption, OpenOption, Path}

import akka.actor.ActorRef
import akka.pattern.ask
import akka.stream.IOResult
import akka.stream.scaladsl.Source
import akka.util.ByteString

import scala.concurrent.{Await, Future}
import scala.concurrent.duration._

/** Test implementation of the proxy for java.nio.Files. For details see inherited trait */
object TestFilesProxy {
  object Actions {
    case class DeleteIfExists(path: Path)
    case class CreateFile(path: Path, attrs: FileAttribute[_]*)
    case class Write(path: Path, bytes: ByteString, options: OpenOption*)
    case class Exists(path: Path, options: LinkOption*)
    case class ReadAllBytes(path: Path)
    case class ReadAsStream(path: Path, chunkSize: Int)
    case class ReadAttributes(path: Path, attributes: String, options: LinkOption*)
    case class List(path: Path)
    case class RamRead(file: RandomAccessFile, offset: Long, size: Long)
    case class StreamCopy(is: FileInputStream, os: FileOutputStream, offset: Long, len: Long)
    case class Move(source: Path, target: Path, options: CopyOption*)
    case class NewFIS(file: File)
    case class NewFOS(file: File)
  }

  object Predicts {
    case class Throw(ex: Throwable)
  }
}

class TestFilesProxy(tpRef: ActorRef) extends FilesProxy {
  import TestFilesProxy._
  override def deleteIfExists(path: Path): Boolean = {
    Await.result(tpRef.ask(Actions.DeleteIfExists(path))(3 second), 3 second) match {
      case Predicts.Throw(ex) => throw ex
      case v: Boolean => v
    }
  }

  override def createFile(path: Path, attrs: FileAttribute[_]*): Path = {
    Await.result(tpRef.ask(Actions.CreateFile(path, attrs: _*))(3 second), 3 second) match {
      case Predicts.Throw(ex) => throw ex
      case v: Path => v
    }
  }

  override def write(path: Path, bytes: Array[Byte], options: OpenOption*): Path = {
    Await.result(tpRef.ask(Actions.Write(path, ByteString(bytes), options: _*))(3 second), 3 second) match {
      case Predicts.Throw(ex) => throw ex
      case v: Path => v
    }
  }

  override def exists(path: Path, options: LinkOption*): Boolean = {
    Await.result(tpRef.ask(Actions.Exists(path, options: _*))(3 second), 3 second) match {
      case Predicts.Throw(ex) => throw ex
      case v: Boolean => v
    }
  }

  override def readAllBytes(path: Path): Array[Byte] = {
    Await.result(tpRef.ask(Actions.ReadAllBytes(path))(3 second), 3 second) match {
      case Predicts.Throw(ex) => throw ex
      case v: Array[Byte] => v
    }
  }

  override def readAsStream(path: Path, chunkSize: Int): Source[ByteString, Future[IOResult]] = {
    Await.result(tpRef.ask(Actions.ReadAsStream(path, chunkSize))(3 second), 3 second) match {
      case Predicts.Throw(ex) => throw ex
      case v: Source[ByteString, Future[IOResult]] => v
    }
  }

  override def readAttributes(path: Path, attributes: String, options: LinkOption*): Map[String, AnyRef] = {
    Await.result(tpRef.ask(Actions.ReadAttributes(path, attributes, options: _*))(3 second), 3 second) match {
      case Predicts.Throw(ex) => throw ex
      case v: Map[String, AnyRef] => v
    }
  }

  override def list(dir: Path): java.util.stream.Stream[Path] = {
    Await.result(tpRef.ask(Actions.List(dir))(3 second), 3 second) match {
      case Predicts.Throw(ex) => throw ex
      case v: java.util.stream.Stream[Path] => v
    }
  }

  override def ramRead(file: RandomAccessFile, offset: Long, size: Long): ByteString = {
    Await.result(tpRef.ask(Actions.RamRead(file, offset, size))(3 second), 3 second) match {
      case Predicts.Throw(ex) => throw ex
      case v: ByteString => v
    }
  }

  override def streamCopy(is: FileInputStream, os: FileOutputStream, offset: Long, len: Long): Boolean = {
    Await.result(tpRef.ask(Actions.StreamCopy(is, os, offset, len))(3 second), 3 second) match {
      case Predicts.Throw(ex) => throw ex
      case v: Boolean => v
    }
  }

  def move(source: Path, target: Path, options: CopyOption*): Path = {
    Await.result(tpRef.ask(Actions.Move(source, target, options: _*))(3 second), 3 second) match {
      case Predicts.Throw(ex) => throw ex
      case v: Path => v
    }
  }

  override def newFOS(file: File): FileOutputStream = {
    Await.result(tpRef.ask(Actions.NewFOS(file))(3 second), 3 second) match {
      case Predicts.Throw(ex) => throw ex
      case v: FileOutputStream => v
    }
  }

  override def newFIS(file: File): FileInputStream = {
    Await.result(tpRef.ask(Actions.NewFIS(file))(3 second), 3 second) match {
      case Predicts.Throw(ex) => throw ex
      case v: FileInputStream => v
    }
  }
}
