package ru.serbis.okto.node.reps

import java.io.{File, FileInputStream, FileOutputStream, RandomAccessFile}
import java.nio.file.attribute.FileTime
import java.nio.file.{Files, Path, StandardOpenOption}

import akka.actor.{Actor, Props}
import akka.stream.scaladsl.Source
import akka.util.ByteString
import ru.serbis.okto.node.log.Logger.LogEntryQualifier
import ru.serbis.okto.node.log.StreamLogger
import ru.serbis.okto.node.proxy.files.FilesProxy
import akka.pattern.pipe
import akka.stream.IOResult
import com.sun.corba.se.spi.orb.OperationFactory
import ru.serbis.okto.node.common.ReachTypes.ReachByteString

import scala.collection.mutable
import scala.concurrent.Future
import scala.concurrent.ExecutionContext.Implicits.global

//TODO создать блокировщик ресуров, что бы предотсвраить несогласованное чтение записаь файлов в асинхронном режиме (таблица файлов над которыми прводится операция и сташ)


/** The repository of the internal node's storage. Provides asynchronous operations on files inside the storage.
  *
  * Attention! The repository currently does not guarantee compliance with the sequence of operations on files. This
  * requirement should be monitored by the requestor.
  */
object StorageRep {
  def props(storagePath: String, files: FilesProxy) = Props(new StorageRep(storagePath, files))

  object Commands {

    /** Read all data from the file and return result as BlobData message
      *
      * @param path path to the file
      */
    case class Read(path: String)

    /** Read fragment from file and respond with BlobData. Negative start value replaced by 0. End value exceeding file
      * size replaced by file size */
    case class ReadFragment(path: String, start: Int, end: Int)

    /** Create stream from file and return it as StreamData message
      *
      * @param chunkSize size of one element of the stream
      * @param path path to the file
      */
    case class ReadAsStream(path: String, chunkSize: Int)

    /** Append data to the file. If file does not exit it will be created
      *
      * @param path path to the file
      * @param data data for write
      */
    case class Append(path: String, data: ByteString)

    /** Rewrite the file with specified data. If file does not exist it will be created
      *
      * @param path path to the file
      * @param data data for write
      */
    case class ReWrite(path: String, data: ByteString)

    /** Replace file fragment started at start and end at start + len with data. Negative start value replaced by 0. End
      * value exceeding file size replaced by file size. If len < 0 if will be replace by 0.
      *
      * @param path path to the file
      * @param start start fragment
      * @param len size of fragment
      * @param data data to replace
      */
    case class WriteFragment(path: String, start: Int, len: Int, data: ByteString)

    /** Delete specified file. Missing file is not considered an error.
      *
      * @param path path to the file
      */
    case class Delete(path: String)

    /** Get info for files specified files. Respond with FilesInfo with map where key requested file name and value info
      * about it or error of which failed to get file info. If files list in original command is empty, info about all files
      * in the storage will be returned. May return OperationError if unable to get access to the storage files list
      *
      * @param files files list
      * @param base read as dir in the storage. This path is relative to the storage dir. For example "dir1/dir2/dir" or ""
      *             for storage root
      **/
    case class GetInfo(files: List[String], base: String)
  }

  object Responses {

    /** Blob data from some file */
    case class BlobData(data: ByteString)

    /** Files information response */
    case class FilesInfo(info: Map[String, Either[Throwable, FileInfo]])

    /** Stream represents some file */
    case class StreamData(stream: Source[ByteString, Future[IOResult]])

    /** Success operation message */
    case object OperationSuccess

    /** Failure operation message
      *
      * @param reason error message
      */
    case class OperationError(reason: String)

    /** File information descriptor */
    case class FileInfo(dir: Boolean, size: Long, created: Long, modified: Long)
  }
}

class StorageRep(dir: String, files: FilesProxy) extends Actor with StreamLogger {
  import StorageRep.Commands._
  import StorageRep.Responses._

  implicit val logQualifier = LogEntryQualifier("static")

  ///** List of file locks. It is used to stash messages on certain files, if at the time of arrival of a message, a certain operation occurs on the file. */
  //var locks: mutable.HashMap[String, mutable.ListBuffer[Any]] = mutable.HashMap.empty

  setLogSourceName(s"StorageRep*${self.path.name}")
  setLogKeys(Seq("StorageRep"))


  //Check for access to the target storage dir
  try {
    val testFilePath = new File(s"$dir/testfile").toPath
    files.deleteIfExists(testFilePath)
    files.createFile(testFilePath)
    files.deleteIfExists(testFilePath)
    logger.info("StorageRep actor is initialized")
  } catch {
    case ex: Throwable =>
      logger.fatal(s"Unable to access the storage directory '$dir'. Reason '${ex.getMessage}'")
      Thread.sleep(3000)
      context.system.terminate()
  }


  override def receive = {

    /** See the message description */
    case Read(path) =>
      implicit val logQualifier = LogEntryQualifier("Read")

      withPathCheck(s"$dir/$path") { p =>
        pipe {
          Future {
            try {
              if (files.exists(p)) {
                val c = ByteString(files.readAllBytes(p))
                logger.debug(s"Fetched full content from file '$p' with size '${c.size}'")
                BlobData(c)
              } else {
                logger.warning(s"Unable to fetched full content from file '$p'. File does not exist")
                OperationError("not exist")
              }
            } catch {
              case ex: Throwable =>
                logger.error(s"Unable to fetched full content from file '$p' because error was occurred '${ex.getMessage}'")
                OperationError(ex.getMessage)
            }
          }
        } to sender
      }

    /** See the message description */
    case ReadFragment(path, start, len) =>
      implicit val logQualifier = LogEntryQualifier("Read")
      println(s"START - ${System.currentTimeMillis()}")
      withPathCheck(s"$dir/$path") { p =>
        pipe {
          Future {
            try {
              if (files.exists(p)) {
                val realSize = files.readAttributes(p, "size")("size").asInstanceOf[Long]
                val factStart = if (start < 0) 0 else {
                  if (start > realSize) realSize else start
                }
                val factLen = if (len < 0) {
                  0
                } else {
                  if (len + factStart > realSize)
                    realSize - factStart
                  else
                    len
                }
                val file = new RandomAccessFile(p.toFile, "rw")

                val c = files.ramRead(file, factStart, factLen)
                logger.debug(s"Fetched fragment content '$factStart+$factLen' from file '$p' with content '${if (c.size > 100) "CUTTED" else c.toHexString}'")
                BlobData(c)
              } else {
                logger.warning(s"Unable to fetched fragment content from file '$p'. File does not exist")
                OperationError("not exist")
              }
            } catch {
              case ex: Throwable =>
                logger.error(s"Unable to fetched fragment content from file '$p' because error was occurred '${ex.getMessage}'")
                OperationError(ex.getMessage)
            }
          }
        } to sender
      }

    /** See the message description */
    case ReadAsStream(path, chunkSize) =>
      implicit val logQualifier = LogEntryQualifier("ReadAsStream")

      withPathCheck(s"$dir/$path") { p =>
        pipe {
          Future {
            try {
              if (files.exists(p)) {
                val c = files.readAsStream(p, chunkSize)
                logger.debug(s"Created stream ref to the file '$p' with chunk size '$chunkSize'")
                StreamData(c)
              } else {
                logger.warning(s"Unable to create stream ref to the file '$p'. File does not exist")
                OperationError("not exist")
              }
            } catch {
              case ex: Throwable =>
                logger.error(s"Unable to create stream ref to the file '$p' because error was occurred '${ex.getMessage}'")
                OperationError(ex.getMessage)
            }
          }
        } to sender
      }

    /** See the message description */
    case Append(path, data) =>
      implicit val logQualifier = LogEntryQualifier("Append")

      withPathCheck(s"$dir/$path") { p =>
        pipe {
          Future {
            try {
              files.createDirectories(p.getParent)
              files.write(p, data.toArray, StandardOpenOption.CREATE, StandardOpenOption.APPEND)
              logger.debug(s"Appended file '$path' with size of data '${data.size}'")
              OperationSuccess
            } catch {
              case ex: Throwable =>
                logger.error(s"Unable to append data to the file '$p' with size '${data.size}' because error was occurred '${ex.getMessage}'")
                OperationError(ex.getMessage)
            }
          }
        } to sender
      }

    /** See the message description */
    case ReWrite(path, data) =>
      implicit val logQualifier = LogEntryQualifier("ReWrite")

      withPathCheck(s"$dir/$path") { p =>
        pipe {
          Future {
            try {
              files.createDirectories(p.getParent)
              files.write(p, data.toArray, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)
              logger.debug(s"Rewrite file '$path' with size of data '${data.size}'")
              OperationSuccess
            } catch {
              case ex: Throwable =>
                logger.error(s"Unable to rewrite file '$p' with data size '${data.size}' because error was occurred '${ex.getMessage}'")
                OperationError(ex.getMessage)
            }
          }
        } to sender
      }

    /** See the message description */
    case WriteFragment(path, start, len, data) =>
      implicit val logQualifier = LogEntryQualifier("WriteFragment")

      withPathCheck(s"$dir/$path") { p =>
        pipe {
          Future {
            try {
              files.createDirectories(p.getParent)
              if (!files.exists(p))
                files.createFile(p)

              val realSize = files.readAttributes(p, "size")("size").asInstanceOf[Long]
              val factStart = if (start < 0) 0 else {
                if (start > realSize) realSize else start
              }
              val factLen = if (len > realSize) realSize else {
                if (len < 0) 0 else len
              }

              val tmpFile = new File(s"$dir/${p.getFileName.getName(0)}.tmp")
              val tmpFilePath = tmpFile.toPath
              files.deleteIfExists(tmpFilePath)
              files.createFile(tmpFilePath)
              val is = files.newFIS(p.toFile)
              val os = files.newFOS(tmpFile)
              files.streamCopy(is, os, 0, factStart) //Copy first part to the tmp file
              os.write(data.toArray) //Write new fragment to the tmp file
              is.close()
              val is2 = files.newFIS(p.toFile)
              files.streamCopy(is2, os, factStart + factLen, realSize - (factStart + factLen)) //Copy last part the tmp file
              is2.close()
              os.close()
              files.deleteIfExists(p) //Delete original file
              files.move(tmpFilePath, p) //Rename tmp file to original file name

              logger.debug(s"Write fragment '$factStart+$factLen' to file '$p' with content '${data.toHexString}'")
              OperationSuccess
            } catch {
              case ex: Throwable =>
                logger.error(s"Unable to write fragment content to file '$p' because error was occurred '${ex.getMessage}'")
                OperationError(ex.getMessage)
            }

          }
        } to sender
      }

    /** See the message description */
    case Delete(path) =>
      implicit val logQualifier = LogEntryQualifier("Delete")

      withPathCheck(s"$dir/$path") { p =>
        pipe {
          Future {
            try {
              files.deleteIfExists(p)
              logger.debug(s"Delete file '$path'")
              OperationSuccess
            } catch {
              case ex: Throwable =>
                logger.error(s"Unable to delete file '$p' because error was occurred '${ex.getMessage}'")
                OperationError(ex.getMessage)
            }
          }
        } to sender
      }

    /** See the message description */
    case GetInfo(filesList, base) =>
      implicit val logQualifier = LogEntryQualifier("GetInfo")

      pipe {
        Future {
          try {
            val list = if (filesList.isEmpty) { //For empty files list, get all files in the storage
              files.list(new File(if (base.isEmpty) dir else s"$dir/$base").toPath).toArray.toList.asInstanceOf[List[Path]].map(m => m.toAbsolutePath)
            } else {
              filesList.map(m => new File(if (base.isEmpty) s"$dir/$m" else s"$dir/$base/$m").toPath)
            }

            val r = list.foldLeft(Map.empty[String, Either[Throwable, FileInfo]]) {(a, m) =>
              try {
                if (!malformedCheck(m))
                  throw new Throwable("Malformed path")
                val ats = files.readAttributes(m, "size,lastModifiedTime,creationTime")
                val info = FileInfo(files.isDirectory(m), ats("size").asInstanceOf[Long],
                  ats("creationTime").asInstanceOf[FileTime].toMillis,
                  ats("lastModifiedTime").asInstanceOf[FileTime].toMillis)
                logger.warning(s"Extracted file info '${m.getFileName.toString} -> $info'")
                a + (m.getFileName.toString -> Right(info))
              } catch {
                case ex: Throwable =>
                  logger.warning(s"Unable to get file info, reason '${ex.getMessage}'")
                  a + (m.getFileName.toString -> Left(ex))
              }
            }
            FilesInfo(r)

          } catch {
            case ex: Throwable =>
              logger.error(s"Unable to get files list, reason '${ex.getMessage}'")
              OperationError(ex.getMessage)
          }
        }
      } to sender
  }

  /** Verifies that the beginning of the canonical file path matches the path to the repository. If the path is correct,
    * then executes the function code. If not, the sender responds with a path vulnerability error */
  def withPathCheck(path: String)(f: Path => Unit) = {
    val file = new File(path)
    //val can = file.getCanonicalPath
    if (!malformedCheck(file.toPath)) {
      logger.warning(s"Unable process operation because file path '$path' is malformed")
      sender ! OperationError("malformed path")
    } else {
      f(file.toPath)
    }
  }

  def malformedCheck(path: Path) = {
    val can = path.toFile.getCanonicalPath
    if (can.indexOf(dir) != 0)
      false
    else
      true
  }
}
