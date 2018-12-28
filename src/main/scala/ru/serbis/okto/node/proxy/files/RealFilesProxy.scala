package ru.serbis.okto.node.proxy.files

import java.io.{File, FileInputStream, FileOutputStream, RandomAccessFile}
import java.nio.file._
import java.nio.file.attribute.FileAttribute

import collection.JavaConverters._
import akka.stream.IOResult
import akka.stream.scaladsl.{FileIO, Source}
import akka.util.ByteString

import scala.annotation.tailrec
import scala.concurrent.Future

/** Production implementation of the proxy for java.nio.Files. For details see inherited trait */
class RealFilesProxy extends FilesProxy {
  override def deleteIfExists(path: Path): Boolean = Files.deleteIfExists(path)

  override def createFile(path: Path, attrs: FileAttribute[_]*): Path = Files.createFile(path, attrs: _*)

  override def createDirectories(path: Path, attrs: FileAttribute[_]*): Path = Files.createDirectories(path, attrs: _*)

  override def isDirectory(path: Path, options: LinkOption*): Boolean = Files.isDirectory(path, options: _*)

  override def write(path: Path, bytes: Array[Byte], options: OpenOption*): Path = Files.write(path, bytes, options: _*)

  override def exists(path: Path, options: LinkOption*): Boolean = Files.exists(path, options: _*)

  override def readAllBytes(path: Path): Array[Byte] = Files.readAllBytes(path)

  override def readAsStream(path: Path, chunkSize: Int): Source[ByteString, Future[IOResult]] = FileIO.fromPath(path, chunkSize)

  override def readAttributes(path: Path, attributes: String, options: LinkOption*): Map[String, AnyRef] = Files.readAttributes(path, attributes, options: _*).asScala.toMap

  override def list(dir: Path): java.util.stream.Stream[Path] = Files.list(dir)

  override def move(source: Path, target: Path, options: CopyOption*): Path = Files.move(source, target, options: _*)

  override def newFOS(file: File): FileOutputStream = new FileOutputStream(file)

  override def newFIS(file: File): FileInputStream = new FileInputStream(file)


  override def ramRead(file: RandomAccessFile, offset: Long, size: Long): ByteString = {
    val arr = Array.fill(size.toInt)(0 toByte)
    file.seek(offset)
    file.read(arr, 0, size.toInt)
    ByteString(arr)
  }

  override def streamCopy(is: FileInputStream, os: FileOutputStream, offset: Long, len: Long): Boolean = {
    @tailrec
    def copy(offCount: Long, count: Long): Boolean = {
      if (offCount >= offset) {
        if (count > 0) {
          val byte = is.read()
          if (byte != -1) {
            os.write(byte)
            copy(offCount + 1, count - 1)
          } else {
            false
          }
        } else {
          true
        }
      } else {
        val byte = is.read()
        if (byte == -1) {
          false
        } else {
          copy(offCount + 1, count)
        }
      }
    }

    copy(0, len)
  }
}
