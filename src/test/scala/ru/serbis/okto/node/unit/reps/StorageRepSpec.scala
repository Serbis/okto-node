package ru.serbis.okto.node.unit.reps

import java.io.{File, FileInputStream, FileOutputStream, RandomAccessFile}
import java.nio.file.attribute.FileTime
import java.nio.file.{Files, Path, StandardOpenOption}
import java.util
import java.util.Arrays
import java.util.concurrent.TimeUnit

import akka.actor.ActorSystem
import akka.stream._
import akka.stream.scaladsl.Source
import akka.stream.stage.{GraphStageLogic, GraphStageWithMaterializedValue, OutHandler}
import akka.testkit.{ImplicitSender, TestKit, TestProbe}
import akka.util.ByteString
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import ru.serbis.okto.node.log.Logger.LogLevels
import ru.serbis.okto.node.log.{StdOutLogger, StreamLogger}
import ru.serbis.okto.node.proxy.files.TestFilesProxy
import ru.serbis.okto.node.reps.StorageRep
import ru.serbis.okto.node.reps.StorageRep.Responses.{FileInfo, FilesInfo}

import scala.concurrent.{Future, Promise}


class StorageRepSpec extends TestKit(ActorSystem("TestSystem")) with ImplicitSender with WordSpecLike with Matchers with StreamLogger with BeforeAndAfterAll {

  implicit val mater = ActorMaterializer()

  override protected def beforeAll(): Unit = {
    initializeGlobalLogger(system, LogLevels.Info)
    logger.addDestination(system.actorOf(StdOutLogger.props, "StdOutLogger"))
  }

  "For Read message" must {
    "Read file and return it content" in {
      val filesProbe = TestProbe()
      val filesProxy = new TestFilesProxy(filesProbe.ref)
      val probe = TestProbe()

      val target = system.actorOf(StorageRep.props("/dist/storage", filesProxy))

      initTarget(filesProbe)

      probe.send(target, StorageRep.Commands.Read("file"))

      val data = List(97.toByte, 98.toByte, 99.toByte).toArray
      val fp = new File("/dist/storage/file").toPath
      filesProbe.expectMsg(TestFilesProxy.Actions.Exists(fp))
      filesProbe.reply(true)
      filesProbe.expectMsg(TestFilesProxy.Actions.ReadAllBytes(fp))
      filesProbe.reply(data)

      probe.expectMsg(StorageRep.Responses.BlobData(ByteString(data)))
    }

    "Return OperationError if specified file does not exist in the storage" in {
      val filesProbe = TestProbe()
      val filesProxy = new TestFilesProxy(filesProbe.ref)
      val probe = TestProbe()

      val target = system.actorOf(StorageRep.props("/dist/storage", filesProxy))

      initTarget(filesProbe)

      probe.send(target, StorageRep.Commands.Read("123"))

      val fp = new File("/dist/storage/123").toPath
      filesProbe.expectMsg(TestFilesProxy.Actions.Exists(fp))
      filesProbe.reply(false)

      probe.expectMsg(StorageRep.Responses.OperationError("not exist"))
    }

    "Return OperationError if some error was occurs at read process" in {
      val filesProbe = TestProbe()
      val filesProxy = new TestFilesProxy(filesProbe.ref)
      val probe = TestProbe()

      val target = system.actorOf(StorageRep.props("/dist/storage", filesProxy))

      initTarget(filesProbe)

      probe.send(target, StorageRep.Commands.Read("123"))

      val fp = new File("/dist/storage/123").toPath
      filesProbe.expectMsg(TestFilesProxy.Actions.Exists(fp))
      filesProbe.reply(TestFilesProxy.Predicts.Throw(new Exception("extext")))

      probe.expectMsg(StorageRep.Responses.OperationError("extext"))
    }

    "Return OperationError path goes outside of the storage" in {
      val filesProbe = TestProbe()
      val filesProxy = new TestFilesProxy(filesProbe.ref)
      val probe = TestProbe()

      val target = system.actorOf(StorageRep.props("/dist/storage", filesProxy))

      initTarget(filesProbe)

      probe.send(target, StorageRep.Commands.Read("../123"))
      probe.expectMsg(StorageRep.Responses.OperationError("malformed path"))
    }
  }

  "For ReadFragment message" must {
    "Read file and return it content" in {
      val filesProbe = TestProbe()
      val filesProxy = new TestFilesProxy(filesProbe.ref)
      val probe = TestProbe()

      val target = system.actorOf(StorageRep.props("/tmp", filesProxy))

      initTarget3(filesProbe)

      probe.send(target, StorageRep.Commands.ReadFragment("file", 50, 100))

      val data = List(97.toByte, 98.toByte, 99.toByte).toArray
      val fp = new File("/tmp/file").toPath
      Files.deleteIfExists(fp)
      Files.createFile(fp)
      filesProbe.expectMsg(TestFilesProxy.Actions.Exists(fp))
      filesProbe.reply(true)
      filesProbe.expectMsg(TestFilesProxy.Actions.ReadAttributes(fp, "size"))
      filesProbe.reply(Map("size" -> 100L))
      val raf = new RandomAccessFile(fp.toFile, "rw")
      val r = filesProbe.expectMsgType[TestFilesProxy.Actions.RamRead]
      r.offset shouldEqual 50
      r.size shouldEqual 50
      filesProbe.reply(ByteString(data))

      probe.expectMsg(StorageRep.Responses.BlobData(ByteString(data)))
    }

    "Replace incorrect input params with correct values" in {
      val filesProbe = TestProbe()
      val filesProxy = new TestFilesProxy(filesProbe.ref)
      val probe = TestProbe()

      val target = system.actorOf(StorageRep.props("/tmp", filesProxy))

      initTarget3(filesProbe)

      probe.send(target, StorageRep.Commands.ReadFragment("file", -1, 1000))

      val data = List(97.toByte, 98.toByte, 99.toByte).toArray
      val fp = new File("/tmp/file").toPath
      Files.deleteIfExists(fp)
      Files.createFile(fp)
      filesProbe.expectMsg(TestFilesProxy.Actions.Exists(fp))
      filesProbe.reply(true)
      filesProbe.expectMsg(TestFilesProxy.Actions.ReadAttributes(fp, "size"))
      filesProbe.reply(Map("size" -> 100L))
      val raf = new RandomAccessFile(fp.toFile, "rw")
      val r = filesProbe.expectMsgType[TestFilesProxy.Actions.RamRead]
      r.offset shouldEqual 0
      r.size shouldEqual 100
      filesProbe.reply(ByteString(data))

      probe.expectMsg(StorageRep.Responses.BlobData(ByteString(data)))
    }

    "Return OperationError if specified file does not exist in the storage" in {
      val filesProbe = TestProbe()
      val filesProxy = new TestFilesProxy(filesProbe.ref)
      val probe = TestProbe()

      val target = system.actorOf(StorageRep.props("/tmp", filesProxy))

      initTarget3(filesProbe)

      probe.send(target, StorageRep.Commands.ReadFragment("file", 50, 100))

      val fp = new File("/tmp/file").toPath
      filesProbe.expectMsg(TestFilesProxy.Actions.Exists(fp))
      filesProbe.reply(false)

      probe.expectMsg(StorageRep.Responses.OperationError("not exist"))
    }

    "Return OperationError if some error was occurs at read process" in {
      val filesProbe = TestProbe()
      val filesProxy = new TestFilesProxy(filesProbe.ref)
      val probe = TestProbe()

      val target = system.actorOf(StorageRep.props("/tmp", filesProxy))

      initTarget3(filesProbe)

      probe.send(target, StorageRep.Commands.ReadFragment("file", 50, 100))

      val fp = new File("/tmp/file").toPath
      filesProbe.expectMsg(TestFilesProxy.Actions.Exists(fp))
      filesProbe.reply(TestFilesProxy.Predicts.Throw(new Exception("extext")))

      probe.expectMsg(StorageRep.Responses.OperationError("extext"))
    }

    "Return OperationError path goes outside of the storage" in {
      val filesProbe = TestProbe()
      val filesProxy = new TestFilesProxy(filesProbe.ref)
      val probe = TestProbe()

      val target = system.actorOf(StorageRep.props("/tmp", filesProxy))

      initTarget3(filesProbe)

      probe.send(target, StorageRep.Commands.ReadFragment("../123", 50, 100))
      probe.expectMsg(StorageRep.Responses.OperationError("malformed path"))
    }
  }

  "For ReadStream message" must {
    "Return stream to the target file" in {
      val filesProbe = TestProbe()
      val filesProxy = new TestFilesProxy(filesProbe.ref)
      val probe = TestProbe()

      val target = system.actorOf(StorageRep.props("/dist/storage", filesProxy))

      initTarget(filesProbe)

      probe.send(target, StorageRep.Commands.ReadAsStream("file", 100))

      val source = Source.fromGraph(new FileSourceStub(new File("123").toPath, 1024, 0))
      val fp = new File("/dist/storage/file").toPath
      filesProbe.expectMsg(TestFilesProxy.Actions.Exists(fp))
      filesProbe.reply(true)
      filesProbe.expectMsg(TestFilesProxy.Actions.ReadAsStream(fp, 100))
      filesProbe.reply(source)

      probe.expectMsg(StorageRep.Responses.StreamData(source))
    }

    "Return OperationError if specified file does not exist in the storage" in {
      val filesProbe = TestProbe()
      val filesProxy = new TestFilesProxy(filesProbe.ref)
      val probe = TestProbe()

      val target = system.actorOf(StorageRep.props("/dist/storage", filesProxy))

      initTarget(filesProbe)

      probe.send(target, StorageRep.Commands.ReadAsStream("123", 100))

      val fp = new File("/dist/storage/123").toPath
      filesProbe.expectMsg(TestFilesProxy.Actions.Exists(fp))
      filesProbe.reply(false)

      probe.expectMsg(StorageRep.Responses.OperationError("not exist"))
    }

    "Return OperationError if some error was occurs at stream creation process" in {
      val filesProbe = TestProbe()
      val filesProxy = new TestFilesProxy(filesProbe.ref)
      val probe = TestProbe()

      val target = system.actorOf(StorageRep.props("/dist/storage", filesProxy))

      initTarget(filesProbe)

      probe.send(target, StorageRep.Commands.ReadAsStream("123", 100))

      val fp = new File("/dist/storage/123").toPath
      filesProbe.expectMsg(TestFilesProxy.Actions.Exists(fp))
      filesProbe.reply(TestFilesProxy.Predicts.Throw(new Exception("extext")))

      probe.expectMsg(StorageRep.Responses.OperationError("extext"))
    }

    "Return OperationError path goes outside of the storage" in {
      val filesProbe = TestProbe()
      val filesProxy = new TestFilesProxy(filesProbe.ref)
      val probe = TestProbe()

      val target = system.actorOf(StorageRep.props("/dist/storage", filesProxy))

      initTarget(filesProbe)

      probe.send(target, StorageRep.Commands.ReadAsStream("../123", 100))
      probe.expectMsg(StorageRep.Responses.OperationError("malformed path"))
    }
  }

  "For Append message" must {
    "Append data to the file" in {
      val filesProbe = TestProbe()
      val filesProxy = new TestFilesProxy(filesProbe.ref)
      val probe = TestProbe()

      val target = system.actorOf(StorageRep.props("/dist/storage", filesProxy))

      initTarget(filesProbe)

      val data = List(97.toByte, 98.toByte, 99.toByte).toArray
      probe.send(target, StorageRep.Commands.Append("file", ByteString(data)))

      val fp = new File("/dist/storage/file").toPath
      filesProbe.expectMsg(TestFilesProxy.Actions.CreateDirectories(fp.getParent))
      filesProbe.reply(fp)
      filesProbe.expectMsg(TestFilesProxy.Actions.Write(fp, ByteString(data), StandardOpenOption.CREATE, StandardOpenOption.APPEND))
      filesProbe.reply(fp)

      probe.expectMsg(StorageRep.Responses.OperationSuccess)
    }

    "Return OperationError if some error was occurs at write process" in {
      val filesProbe = TestProbe()
      val filesProxy = new TestFilesProxy(filesProbe.ref)
      val probe = TestProbe()

      val target = system.actorOf(StorageRep.props("/dist/storage", filesProxy))

      initTarget(filesProbe)

      val data = List(97.toByte, 98.toByte, 99.toByte).toArray
      probe.send(target, StorageRep.Commands.Append("file", ByteString(data)))

      val fp = new File("/dist/storage/file").toPath
      filesProbe.expectMsg(TestFilesProxy.Actions.CreateDirectories(fp.getParent))
      filesProbe.reply(fp)
      filesProbe.expectMsg(TestFilesProxy.Actions.Write(fp, ByteString(data), StandardOpenOption.CREATE, StandardOpenOption.APPEND))
      filesProbe.reply(TestFilesProxy.Predicts.Throw(new Exception("extext")))

      probe.expectMsg(StorageRep.Responses.OperationError("extext"))
    }

    "Return OperationError path goes outside of the storage" in {
      val filesProbe = TestProbe()
      val filesProxy = new TestFilesProxy(filesProbe.ref)
      val probe = TestProbe()

      val target = system.actorOf(StorageRep.props("/dist/storage", filesProxy))

      initTarget(filesProbe)

      val data = List(97.toByte, 98.toByte, 99.toByte).toArray
      probe.send(target, StorageRep.Commands.Append("../file", ByteString(data)))
      probe.expectMsg(StorageRep.Responses.OperationError("malformed path"))
    }
  }

  "For ReWrite message" must {
    "Rewrite data in the file" in {
      val filesProbe = TestProbe()
      val filesProxy = new TestFilesProxy(filesProbe.ref)
      val probe = TestProbe()

      val target = system.actorOf(StorageRep.props("/dist/storage", filesProxy))

      initTarget(filesProbe)

      val data = List(97.toByte, 98.toByte, 99.toByte).toArray
      probe.send(target, StorageRep.Commands.ReWrite("file", ByteString(data)))

      val fp = new File("/dist/storage/file").toPath
      filesProbe.expectMsg(TestFilesProxy.Actions.CreateDirectories(fp.getParent))
      filesProbe.reply(fp)
      filesProbe.expectMsg(TestFilesProxy.Actions.Write(fp, ByteString(data), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))
      filesProbe.reply(fp)

      probe.expectMsg(StorageRep.Responses.OperationSuccess)
    }

    "Return OperationError if some error was occurs at write process" in {
      val filesProbe = TestProbe()
      val filesProxy = new TestFilesProxy(filesProbe.ref)
      val probe = TestProbe()

      val target = system.actorOf(StorageRep.props("/dist/storage", filesProxy))

      initTarget(filesProbe)

      val data = List(97.toByte, 98.toByte, 99.toByte).toArray
      probe.send(target, StorageRep.Commands.ReWrite("file", ByteString(data)))

      val fp = new File("/dist/storage/file").toPath
      filesProbe.expectMsg(TestFilesProxy.Actions.CreateDirectories(fp.getParent))
      filesProbe.reply(fp)
      filesProbe.expectMsg(TestFilesProxy.Actions.Write(fp, ByteString(data), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING))
      filesProbe.reply(TestFilesProxy.Predicts.Throw(new Exception("extext")))

      probe.expectMsg(StorageRep.Responses.OperationError("extext"))
    }

    "Return OperationError path goes outside of the storage" in {
      val filesProbe = TestProbe()
      val filesProxy = new TestFilesProxy(filesProbe.ref)
      val probe = TestProbe()

      val target = system.actorOf(StorageRep.props("/dist/storage", filesProxy))

      initTarget(filesProbe)

      val data = List(97.toByte, 98.toByte, 99.toByte).toArray
      probe.send(target, StorageRep.Commands.ReWrite("../file", ByteString(data)))
      probe.expectMsg(StorageRep.Responses.OperationError("malformed path"))
    }
  }

  "For Delete message" must {
    "Delete specified file" in {
      val filesProbe = TestProbe()
      val filesProxy = new TestFilesProxy(filesProbe.ref)
      val probe = TestProbe()

      val target = system.actorOf(StorageRep.props("/dist/storage", filesProxy))

      initTarget(filesProbe)

      val data = List(97.toByte, 98.toByte, 99.toByte).toArray
      probe.send(target, StorageRep.Commands.Delete("file"))

      val fp = new File("/dist/storage/file").toPath
      filesProbe.expectMsg(TestFilesProxy.Actions.DeleteIfExists(fp))
      filesProbe.reply(true)

      probe.expectMsg(StorageRep.Responses.OperationSuccess)
    }

    "Return OperationError if some error was occurs at write process" in {
      val filesProbe = TestProbe()
      val filesProxy = new TestFilesProxy(filesProbe.ref)
      val probe = TestProbe()

      val target = system.actorOf(StorageRep.props("/dist/storage", filesProxy))

      initTarget(filesProbe)

      val data = List(97.toByte, 98.toByte, 99.toByte).toArray
      probe.send(target, StorageRep.Commands.Delete("file"))

      val fp = new File("/dist/storage/file").toPath
      filesProbe.expectMsg(TestFilesProxy.Actions.DeleteIfExists(fp))
      filesProbe.reply(TestFilesProxy.Predicts.Throw(new Exception("extext")))

      probe.expectMsg(StorageRep.Responses.OperationError("extext"))
    }

    "Return OperationError path goes outside of the storage" in {
      val filesProbe = TestProbe()
      val filesProxy = new TestFilesProxy(filesProbe.ref)
      val probe = TestProbe()

      val target = system.actorOf(StorageRep.props("/dist/storage", filesProxy))

      initTarget(filesProbe)

      probe.send(target, StorageRep.Commands.Delete("../file"))
      probe.expectMsg(StorageRep.Responses.OperationError("malformed path"))
    }
  }

  "For GetInfo message" must {
    "For empty files list must return info about all files in the storage" in {
      val filesProbe = TestProbe()
      val filesProxy = new TestFilesProxy(filesProbe.ref)
      val probe = TestProbe()

      val target = system.actorOf(StorageRep.props(new File("dist/storage").toPath.toAbsolutePath.toString, filesProxy))

      initTarget2(filesProbe)

      probe.send(target, StorageRep.Commands.GetInfo(List.empty, "dir"))

      val path1 = new File("dist/storage/dir/a").toPath.toAbsolutePath
      val path2 = new File("dist/storage/dir/b").toPath.toAbsolutePath
      val path3 = new File("dist/storage/dir/c").toPath.toAbsolutePath

      val fp = new File(new File("dist/storage/dir").toPath.toAbsolutePath.toString).toPath
      filesProbe.expectMsg(TestFilesProxy.Actions.List(fp))
      filesProbe.reply(util.Arrays.stream(Array(path1, path2, path3)))
      filesProbe.expectMsg(TestFilesProxy.Actions.ReadAttributes(path1, "size,lastModifiedTime,creationTime"))
      filesProbe.reply(Map(
        "size" -> 100L,
        "lastModifiedTime" -> FileTime.from(1000, TimeUnit.SECONDS),
        "creationTime" -> FileTime.from(2000, TimeUnit.SECONDS)
      ))
      filesProbe.expectMsg(TestFilesProxy.Actions.IsDirectory(path1))
      filesProbe.reply(true)
      filesProbe.expectMsg(TestFilesProxy.Actions.ReadAttributes(path2, "size,lastModifiedTime,creationTime"))
      filesProbe.reply(Map(
        "size" -> 101L,
        "lastModifiedTime" -> FileTime.from(1001, TimeUnit.SECONDS),
        "creationTime" -> FileTime.from(2001, TimeUnit.SECONDS)
      ))
      filesProbe.expectMsg(TestFilesProxy.Actions.IsDirectory(path2))
      filesProbe.reply(false)
      filesProbe.expectMsg(TestFilesProxy.Actions.ReadAttributes(path3, "size,lastModifiedTime,creationTime"))
      filesProbe.reply(Map(
        "size" -> 102L,
        "lastModifiedTime" -> FileTime.from(1002, TimeUnit.SECONDS),
        "creationTime" -> FileTime.from(2002, TimeUnit.SECONDS)
      ))
      filesProbe.expectMsg(TestFilesProxy.Actions.IsDirectory(path3))
      filesProbe.reply(false)
      probe.expectMsg(StorageRep.Responses.FilesInfo(Map(
        "a" -> Right(FileInfo(true, 100, 2000000, 1000000)),
        "b" -> Right(FileInfo(false, 101, 2001000, 1001000)),
        "c" -> Right(FileInfo(false, 102, 2002000, 1002000))
      )))
    }

    "For not empty files list must return info about this files" in {
      val filesProbe = TestProbe()
      val filesProxy = new TestFilesProxy(filesProbe.ref)
      val probe = TestProbe()

      val target = system.actorOf(StorageRep.props(new File("dist/storage").toPath.toAbsolutePath.toString, filesProxy))

      initTarget2(filesProbe)

      probe.send(target, StorageRep.Commands.GetInfo(List("a", "b", "c"), "dir"))

      val path1 = new File("dist/storage/dir/a").toPath.toAbsolutePath
      val path2 = new File("dist/storage/dir/b").toPath.toAbsolutePath
      val path3 = new File("dist/storage/dir/c").toPath.toAbsolutePath

      filesProbe.expectMsg(TestFilesProxy.Actions.ReadAttributes(path1, "size,lastModifiedTime,creationTime"))
      filesProbe.reply(Map(
        "size" -> 100L,
        "lastModifiedTime" -> FileTime.from(1000, TimeUnit.SECONDS),
        "creationTime" -> FileTime.from(2000, TimeUnit.SECONDS)
      ))
      filesProbe.expectMsg(TestFilesProxy.Actions.IsDirectory(path1))
      filesProbe.reply(true)
      filesProbe.expectMsg(TestFilesProxy.Actions.ReadAttributes(path2, "size,lastModifiedTime,creationTime"))
      filesProbe.reply(Map(
        "size" -> 101L,
        "lastModifiedTime" -> FileTime.from(1001, TimeUnit.SECONDS),
        "creationTime" -> FileTime.from(2001, TimeUnit.SECONDS)
      ))
      filesProbe.expectMsg(TestFilesProxy.Actions.IsDirectory(path2))
      filesProbe.reply(false)
      filesProbe.expectMsg(TestFilesProxy.Actions.ReadAttributes(path3, "size,lastModifiedTime,creationTime"))
      filesProbe.reply(Map(
        "size" -> 102L,
        "lastModifiedTime" -> FileTime.from(1002, TimeUnit.SECONDS),
        "creationTime" -> FileTime.from(2002, TimeUnit.SECONDS)
      ))
      filesProbe.expectMsg(TestFilesProxy.Actions.IsDirectory(path3))
      filesProbe.reply(false)

      probe.expectMsg(StorageRep.Responses.FilesInfo(Map(
        "a" -> Right(FileInfo(true, 100, 2000000, 1000000)),
        "b" -> Right(FileInfo(false, 101, 2001000, 1001000)),
        "c" -> Right(FileInfo(false, 102, 2002000, 1002000))
      )))
    }

    "Return partial result if for some files was thrown an exception" in {
      val filesProbe = TestProbe()
      val filesProxy = new TestFilesProxy(filesProbe.ref)
      val probe = TestProbe()

      val target = system.actorOf(StorageRep.props(new File("dist/storage").toPath.toAbsolutePath.toString, filesProxy))

      initTarget2(filesProbe)

      probe.send(target, StorageRep.Commands.GetInfo(List("a", "b", "c"), ""))

      val path1 = new File("dist/storage/a").toPath.toAbsolutePath
      val path2 = new File("dist/storage/b").toPath.toAbsolutePath
      val path3 = new File("dist/storage/c").toPath.toAbsolutePath

      filesProbe.expectMsg(TestFilesProxy.Actions.ReadAttributes(path1, "size,lastModifiedTime,creationTime"))
      filesProbe.reply(Map(
        "size" -> 100L,
        "lastModifiedTime" -> FileTime.from(1000, TimeUnit.SECONDS),
        "creationTime" -> FileTime.from(2000, TimeUnit.SECONDS)
      ))
      filesProbe.expectMsg(TestFilesProxy.Actions.IsDirectory(path1))
      filesProbe.reply(false)
      filesProbe.expectMsg(TestFilesProxy.Actions.ReadAttributes(path2, "size,lastModifiedTime,creationTime"))
      val tr = new Throwable("x")
      filesProbe.reply(TestFilesProxy.Predicts.Throw(tr))
      filesProbe.expectMsg(TestFilesProxy.Actions.ReadAttributes(path3, "size,lastModifiedTime,creationTime"))
      filesProbe.reply(Map(
        "size" -> 102L,
        "lastModifiedTime" -> FileTime.from(1002, TimeUnit.SECONDS),
        "creationTime" -> FileTime.from(2002, TimeUnit.SECONDS)
      ))
      filesProbe.expectMsg(TestFilesProxy.Actions.IsDirectory(path3))
      filesProbe.reply(false)

      probe.expectMsg(StorageRep.Responses.FilesInfo(Map(
        "a" -> Right(FileInfo(false, 100, 2000000, 1000000)),
        "b" -> Left(tr),
        "c" -> Right(FileInfo(false, 102, 2002000, 1002000))
      )))
    }

    "Return partial error for malformed path" in {
      val filesProbe = TestProbe()
      val filesProxy = new TestFilesProxy(filesProbe.ref)
      val probe = TestProbe()

      val target = system.actorOf(StorageRep.props(new File("dist/storage").toPath.toAbsolutePath.toString, filesProxy))

      initTarget2(filesProbe)

      probe.send(target, StorageRep.Commands.GetInfo(List("a", "../b", "c"), ""))

      val path1 = new File("dist/storage/a").toPath.toAbsolutePath
      val path3 = new File("dist/storage/c").toPath.toAbsolutePath

      filesProbe.expectMsg(TestFilesProxy.Actions.ReadAttributes(path1, "size,lastModifiedTime,creationTime"))
      filesProbe.reply(Map(
        "size" -> 100L,
        "lastModifiedTime" -> FileTime.from(1000, TimeUnit.SECONDS),
        "creationTime" -> FileTime.from(2000, TimeUnit.SECONDS)
      ))
      filesProbe.expectMsg(TestFilesProxy.Actions.IsDirectory(path1))
      filesProbe.reply(false)
      filesProbe.expectMsg(TestFilesProxy.Actions.ReadAttributes(path3, "size,lastModifiedTime,creationTime"))
      filesProbe.reply(Map(
        "size" -> 102L,
        "lastModifiedTime" -> FileTime.from(1002, TimeUnit.SECONDS),
        "creationTime" -> FileTime.from(2002, TimeUnit.SECONDS)
      ))
      filesProbe.expectMsg(TestFilesProxy.Actions.IsDirectory(path3))
      filesProbe.reply(false)

      val r = probe.expectMsgType[StorageRep.Responses.FilesInfo]
      r.info("a") shouldEqual Right(FileInfo(false, 100, 2000000, 1000000))
      r.info("b").left.get.getMessage shouldEqual "Malformed path"
      r.info("c") shouldEqual Right(FileInfo(false, 102, 2002000, 1002000))

    }

    "Return operation error if storage reading throw error" in {
      val filesProbe = TestProbe()
      val filesProxy = new TestFilesProxy(filesProbe.ref)
      val probe = TestProbe()

      val target = system.actorOf(StorageRep.props(new File("dist/storage").toPath.toAbsolutePath.toString, filesProxy))

      initTarget2(filesProbe)

      probe.send(target, StorageRep.Commands.GetInfo(List.empty, ""))

      val fp = new File(new File("dist/storage").toPath.toAbsolutePath.toString).toPath
      filesProbe.expectMsg(TestFilesProxy.Actions.List(fp))
      val tr = new Throwable("x")
      filesProbe.reply(TestFilesProxy.Predicts.Throw(tr))

      probe.expectMsg(StorageRep.Responses.OperationError(tr.getMessage))
    }
  }

  "For WriteFragment message" must {
    "Replace fragment if the the file" in {
      val filesProbe = TestProbe()
      val filesProxy = new TestFilesProxy(filesProbe.ref)
      val probe = TestProbe()

      val target = system.actorOf(StorageRep.props("/dist/storage", filesProxy))

      initTarget(filesProbe)

      val data = ByteString("abc")
      probe.send(target, StorageRep.Commands.WriteFragment("file", 5, 10, data))

      val fp = new File("/dist/storage/file").toPath
      filesProbe.expectMsg(TestFilesProxy.Actions.CreateDirectories(fp.getParent))
      filesProbe.reply(fp)
      filesProbe.expectMsg(TestFilesProxy.Actions.Exists(fp))
      filesProbe.reply(false)
      filesProbe.expectMsg(TestFilesProxy.Actions.CreateFile(fp))
      filesProbe.reply(fp)
      filesProbe.expectMsg(TestFilesProxy.Actions.ReadAttributes(fp, "size"))
      filesProbe.reply(Map("size" -> 20L))
      val m1 = filesProbe.expectMsgType[TestFilesProxy.Actions.DeleteIfExists]
      val tmpFp = m1.path
      tmpFp.getFileName.getName(0).toString shouldEqual "file.tmp"
      filesProbe.reply(true)
      filesProbe.expectMsg(TestFilesProxy.Actions.CreateFile(tmpFp))
      filesProbe.reply(tmpFp)

      val fectFile = new File("fect/test")
      Files.deleteIfExists(fectFile.toPath)
      Files.createFile(fectFile.toPath)
      val fis = new FileInputStream(fectFile)
      val fis2 = new FileInputStream(fectFile)
      val fos = new FileOutputStream(fectFile)
      filesProbe.expectMsg(TestFilesProxy.Actions.NewFIS(fp.toFile))
      filesProbe.reply(fis)
      filesProbe.expectMsg(TestFilesProxy.Actions.NewFOS(tmpFp.toFile))
      filesProbe.reply(fos)

      filesProbe.expectMsg(TestFilesProxy.Actions.StreamCopy(fis, fos, 0, 5))
      filesProbe.reply(true)
      filesProbe.expectMsg(TestFilesProxy.Actions.NewFIS(fp.toFile))
      filesProbe.reply(fis2)
      filesProbe.expectMsg(TestFilesProxy.Actions.StreamCopy(fis2, fos, 15, 5))
      filesProbe.reply(true)
      filesProbe.expectMsg(TestFilesProxy.Actions.DeleteIfExists(fp))
      filesProbe.reply(true)
      filesProbe.expectMsg(TestFilesProxy.Actions.Move(tmpFp, fp))
      filesProbe.reply(fp)

      probe.expectMsg(StorageRep.Responses.OperationSuccess)
    }

    "Return OperationError if some error was occurs at write process" in {
      val filesProbe = TestProbe()
      val filesProxy = new TestFilesProxy(filesProbe.ref)
      val probe = TestProbe()

      val target = system.actorOf(StorageRep.props("/dist/storage", filesProxy))

      initTarget(filesProbe)

      val data = ByteString("abc")
      probe.send(target, StorageRep.Commands.WriteFragment("file", 5, 10, data))

      val fp = new File("/dist/storage/file").toPath
      filesProbe.expectMsg(TestFilesProxy.Actions.CreateDirectories(fp.getParent))
      filesProbe.reply(fp)
      filesProbe.expectMsg(TestFilesProxy.Actions.Exists(fp))
      filesProbe.reply(TestFilesProxy.Predicts.Throw(new Exception("extext")))

      probe.expectMsg(StorageRep.Responses.OperationError("extext"))
    }

    "Return OperationError path goes outside of the storage" in {
      val filesProbe = TestProbe()
      val filesProxy = new TestFilesProxy(filesProbe.ref)
      val probe = TestProbe()

      val target = system.actorOf(StorageRep.props("/dist/storage", filesProxy))

      initTarget(filesProbe)

      val data = ByteString("abc")
      probe.send(target, StorageRep.Commands.WriteFragment("../file", 5, 10, data))
      probe.expectMsg(StorageRep.Responses.OperationError("malformed path"))
    }
  }
  
  def initTarget(filesProbe: TestProbe): Unit = {
    //Storage access check
    val tp = new File("/dist/storage/testfile").toPath
    filesProbe.expectMsg(TestFilesProxy.Actions.DeleteIfExists(tp))
    filesProbe.reply(true)
    filesProbe.expectMsg(TestFilesProxy.Actions.CreateFile(tp))
    filesProbe.reply(tp)
    filesProbe.expectMsg(TestFilesProxy.Actions.DeleteIfExists(tp))
    filesProbe.reply(true)
  }

  def initTarget2(filesProbe: TestProbe): Unit = {
    //Storage access check
    val tp = new File(s"${new File("dist/storage/testfile").toPath.toAbsolutePath}").toPath
    filesProbe.expectMsg(TestFilesProxy.Actions.DeleteIfExists(tp))
    filesProbe.reply(true)
    filesProbe.expectMsg(TestFilesProxy.Actions.CreateFile(tp))
    filesProbe.reply(tp)
    filesProbe.expectMsg(TestFilesProxy.Actions.DeleteIfExists(tp))
    filesProbe.reply(true)
  }

  def initTarget3(filesProbe: TestProbe): Unit = {
    //Storage access check
    val tp = new File(s"${new File("/tmp/testfile").toPath.toAbsolutePath}").toPath
    filesProbe.expectMsg(TestFilesProxy.Actions.DeleteIfExists(tp))
    filesProbe.reply(true)
    filesProbe.expectMsg(TestFilesProxy.Actions.CreateFile(tp))
    filesProbe.reply(tp)
    filesProbe.expectMsg(TestFilesProxy.Actions.DeleteIfExists(tp))
    filesProbe.reply(true)
  }

  class FileSourceStub(path: Path, chunkSize: Int, startPosition: Long)
    extends GraphStageWithMaterializedValue[SourceShape[ByteString], Future[IOResult]] {

    val out = Outlet[ByteString]("FileSource.out")

    override val shape = SourceShape(out)

    override def createLogicAndMaterializedValue(inheritedAttributes: Attributes) = {
      val logic = new GraphStageLogic(shape) with OutHandler { handler =>
        override def onPull() = {}
      }

      (logic, Promise[IOResult]().future)
    }
  }
}