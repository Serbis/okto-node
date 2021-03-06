package ru.serbis.okto.node.exps

import java.io.File
import java.net.{Socket, StandardSocketOptions}
import java.nio.file.{Files, StandardOpenOption}

import javax.script.{Invocable, ScriptContext, ScriptEngineManager}
import akka.actor.{Actor, ActorSystem, Props, Status}
import akka.io.{IO, Tcp}
import akka.stream.{ActorMaterializer, Attributes, Inlet, SinkShape}
import akka.util.{ByteString, Timeout}
import ch.jodersky.jni.nativeLoader
import ru.serbis.okto.node.common.NodeUtils
import ru.serbis.okto.node.hardware.NativeApi
import ru.serbis.okto.node.common.ReachTypes.ReachVector
import ru.serbis.okto.node.proxy.files.RealFilesProxy
import ru.serbis.okto.node.reps.StorageRep
import akka.pattern.ask
import akka.stream.Attributes.InputBuffer
import akka.stream.impl.{Buffer, QueueSink}
import akka.stream.scaladsl.{FileIO, Sink, SinkQueueWithCancel, Source}
import akka.stream.stage.{GraphStageLogic, GraphStageWithMaterializedValue, InHandler}
import jdk.nashorn.internal.parser.Parser
import jdk.nashorn.internal.runtime.{Context, ErrorManager}
import jdk.nashorn.internal.runtime.options.Options
import ru.serbis.okto.node.log.Logger.{LogEntryQualifier, LogLevels}
import ru.serbis.okto.node.log.{StdOutLogger, StreamLogger}

import scala.concurrent.duration._
import scala.concurrent.{Await, Future, Promise}
import scala.util.{Failure, Success, Try}
import scala.util.control.NonFatal
import scala.util.matching.Regex
import scala.util.matching.Regex.Match


class Work extends Runnable {
  var r: List[Int] = List.empty

  override def run() = {
    val x = (1 to Int.MaxValue).map(v => {
      try {
        Thread.sleep(1000)
        v - 10
      } catch {
        case e: Exception =>
          println("Interrupted")
          v - 20
      }
    }) toList

    r = x
  }
}

object Exps extends /*App with*/ StreamLogger {
  implicit val system = ActorSystem("my-system")
  implicit val materializer = ActorMaterializer()
  implicit val executionContext = system.dispatcher
  implicit val askTimeout = Timeout(10 second)
  implicit val logQualifier = LogEntryQualifier("Exps")

  initializeGlobalLogger(system, LogLevels.Error)
  logger.addDestination(system.actorOf(StdOutLogger.props, "StdOutLogger"))

  val a = 0xaaaaaa01
  val b = Integer.reverseBytes(a)
  val c = 2863311361L

  printf(s"$a$b$c")




  /*val engine = new ScriptEngineManager().getEngineByName("nashorn")
  val context = engine.getContext

  context.setAttribute("sleep", (ms: Int) => Thread.sleep(ms), ScriptContext.ENGINE_SCOPE)
  context.setAttribute("stop", () => {
    throw new ScriptTerminated
    0
  }, ScriptContext.ENGINE_SCOPE)

  val js = """function sig() { print("SIG"); stop(); } while(true) { sleep(1000); print("x"); }""" //

  Future {
    try {
      engine.eval(js)
    } catch {
      case e: ScriptTerminated =>
        //engine.
        println("Script finished")
      case e: Throwable => e.printStackTrace()
    }

  }
  Thread.sleep(3000)
  engine.asInstanceOf[Invocable].invokeFunction("sig")*/

  /*val file = new File("dist/node/storage/tefi").toPath
  Files.deleteIfExists(file)
  Files.createFile(file)
  Files.write(file, Array.fill(10000)("1".charAt(0).toByte), StandardOpenOption.APPEND)
  Files.write(file, Array.fill(10000)("2".charAt(0).toByte), StandardOpenOption.APPEND)
  Files.write(file, Array.fill(10000)("3".charAt(0).toByte), StandardOpenOption.APPEND)
  Files.write(file, Array.fill(10000)("4".charAt(0).toByte), StandardOpenOption.APPEND)
  Files.write(file, Array.fill(10000)("5".charAt(0).toByte), StandardOpenOption.APPEND)
  Files.write(file, Array.fill(10000)("6".charAt(0).toByte), StandardOpenOption.APPEND)
  Files.write(file, Array.fill(10000)("7".charAt(0).toByte), StandardOpenOption.APPEND)
  println("OK")*/
  //val storage = system.actorOf(StorageRep.props("/tmp", new RealFilesProxy))
  //storage ! StorageRep.Commands.WriteFragment("tf2", 0, 0, ByteString("X"))

  /*import javax.script.ScriptEngineManager

  val factory = new ScriptEngineManager

  val engine = factory.getEngineByName("nashorn")
  val context = engine.getContext

  val env = new ScriptEnv(999)

  val timeFunc: () => Unit = () => env.time()

  val vect = Vector("cmd", "code")
  val code = 0
  val message = "xxx"


  context.setAttribute("time", timeFunc, ScriptContext.ENGINE_SCOPE)
  context.setAttribute("stdOut", env.stdOut, ScriptContext.ENGINE_SCOPE)
  context.setAttribute("stdOut", env.stdOut, ScriptContext.ENGINE_SCOPE)


  engine.eval(
    """
       main(['aaa']);

       function main(args) {
        print('x');
       }
    """)

  println("ok")*/

  //val storage = system.actorOf(StorageRep.props("/home/serbis/code/main/MRCS/node_scala/dist/node/storage", new RealFilesProxy))
  //val r = Await.result(storage ? StorageRep.Commands.Delete("file"), 10 second)
  //println(r)

  /*val source = FileIO.fromPath(new File("/").toPath, 1)
  val consumer = system.actorOf(Props(new Consumer))
  val sink = Sink.actorRefWithAck(consumer, "init", "ack", "completed")
  Sink.queue()
  val shape = source to sink
  shape run()*/

}
//возращать данные через футуру после получения новый порции от потока либо ее отсутвия

class Consumer extends Actor {
  override def receive = {
    case "init" =>
      println("init")
      sender() ! "ack"
    case "completed" => println("completed")
    case Status.Failure => println("failure")
    case v: Int =>
      println(s"Value - $v")
      sender() ! "ack"
  }
}


class ScriptEnv(pid: Int) {
  val stdOut = new VStdOut(pid)

  def time() = println(System.currentTimeMillis())
  def exit(code: Int) = println(s"Process $code was exited with code $pid")
}

class VStdOut(pid: Int) {
  def write(str: String) = println(s"Write '$str' to $pid")
}

/*class VStdIn() {
  def readToEOI() = "zxv"
}

class VStdIn() {
  def readToEOI() = "zxv"
}*/


/*class MyJavaClass {
  def add(a: Int, b: Int) = new Result(a + b)
}

class Result(value: Int) {
  def get() = value
}*/
//case class Result(value: Int)
