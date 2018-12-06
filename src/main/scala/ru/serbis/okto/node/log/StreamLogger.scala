package ru.serbis.okto.node.log

import akka.actor.{ActorRef, ActorSystem}
import akka.routing.{BroadcastRoutingLogic, RoundRobinRoutingLogic}
import akka.stream.scaladsl.{Sink, Source}
import akka.stream.{ActorMaterializer, OverflowStrategy}
import ru.serbis.okto.node.log.Logger.LogLevels.LogLevel
import ru.serbis.okto.node.streams.RouterSink2
import ru.serbis.okto.node.streams.RouterSink2.{AddDestination, BroadcastStrategy, RemoveDestination}

object GlobalLoggerData {
  var globLogger: Option[Logger] = None
  var keyMapping: Seq[Any] = Seq.empty
}

trait StreamLogger {
  import GlobalLoggerData._
  import Logger._

  implicit var logSource = LogSource("ND")
  implicit var logKeywords = Seq.empty[Any]
  //implicit val qualifier = LogEntryQualifier("_")

  /**
    * Initialize global logger object. This func must call at the start of
    * the system. Call logging methods without this initialization leads to
    * the throw IllegalStateException.
    *
    * @param system actor system in which the logger will work
    */
  def initializeGlobalLogger(system: ActorSystem, logLevel: LogLevel = LogLevels.Debug, keyMap: Seq[Any] = Seq.empty): Unit = {
    keyMapping = keyMap
    globLogger = Some(new Logger(system, logLevel))
  }

  /**
    * Reset the logger global configuration
    */
  def resetLogger(): Unit = globLogger = None

  def setLogSourceName(name: String): Unit = logSource = LogSource(name)

  def setLogKeys(keys: Seq[Any]): Unit = logKeywords = keys

  def setGlobalLogLevel(level: LogLevels.LogLevel) = {
    if (globLogger.isDefined)
      globLogger.get.internalLogLevel = level
  }

  def setGlobalLogKeys(keyMap: Seq[Any]) = GlobalLoggerData.keyMapping = keyMap

  /**
    * Return global logger instance
    *
    * @return logger instance
    */
  def logger: Logger = {
    if (globLogger.isDefined)
      globLogger.get
    else
      throw new IllegalStateException("Global stream logger object is not configured")
  }
}

object Logger {
  trait LogMessage
  case class FatalMessage(msg: String, from: LogSource, qualifier: LogEntryQualifier) extends LogMessage
  case class ErrorMessage(msg: String, from: LogSource, qualifier: LogEntryQualifier) extends LogMessage
  case class DebugMessage(msg:  String, from: LogSource, qualifier: LogEntryQualifier) extends LogMessage
  case class WarningMessage(msg: String, from: LogSource, qualifier: LogEntryQualifier) extends LogMessage
  case class InfoMessage(msg: String, from: LogSource, qualifier: LogEntryQualifier) extends LogMessage

  case class LogSource(name: String)
  case class LogEntryQualifier(name: String)

  object LogLevels extends Enumeration {
    type LogLevel = Value
    val Debug = Value(0)
    val Info = Value(1)
    val Warning = Value(2)
    val Error = Value(3)
    val Fatal = Value(4)
  }
}

/**
  * Logger logic implementation
  *
  * @param system actor system in which the logger will work
  */
sealed class Logger(system: ActorSystem, logLevel: LogLevel) {
  import Logger._

  implicit val aSyst = system
  implicit val materializer = ActorMaterializer()

  val source = Source.queue[Any](100, OverflowStrategy.backpressure)
  val sink = Sink.fromGraph(new RouterSink2(system, BroadcastStrategy(), BroadcastRoutingLogic()))
  val queue = source.to(sink).run()
  var internalLogLevel = logLevel

  def addDestination(ref: ActorRef) = queue.offer(AddDestination(ref))

  def removeDestination(ref: ActorRef) = queue.offer(RemoveDestination(ref))

  def fatal(msg : => String)(implicit from: LogSource, keys: Seq[Any], qualifier: LogEntryQualifier) = {
    if (internalLogLevel <= LogLevels.Fatal)
      write(queue.offer(FatalMessage(msg, from, qualifier)))
  }

  def error(msg : => String)(implicit from: LogSource, keys: Seq[Any], qualifier: LogEntryQualifier) = {
    if (internalLogLevel <= LogLevels.Error)
      write(queue.offer(ErrorMessage(msg, from, qualifier)))
  }

  def warning(msg : => String)(implicit from: LogSource, keys: Seq[Any], qualifier: LogEntryQualifier) = {
    if (internalLogLevel <= LogLevels.Warning)
      write(queue.offer(WarningMessage(msg, from, qualifier)))
  }

  def debug(msg : => String)(implicit from: LogSource, keys: Seq[Any], qualifier: LogEntryQualifier) = {
    if (internalLogLevel <= LogLevels.Debug)
      write(queue.offer(DebugMessage(msg, from, qualifier)))
  }

  def info(msg : => String)(implicit from: LogSource, keys: Seq[Any], qualifier: LogEntryQualifier) = {
    if (internalLogLevel <= LogLevels.Info)
      write(queue.offer(InfoMessage(msg, from, qualifier)))
  }

  private def write(f: => Any)(implicit from: LogSource, keys: Seq[Any], qualifier: LogEntryQualifier) {
    if (GlobalLoggerData.keyMapping.nonEmpty) {
      if (GlobalLoggerData.keyMapping.intersect(keys).nonEmpty)
        f
    } else {
      f
    }
  }
}
