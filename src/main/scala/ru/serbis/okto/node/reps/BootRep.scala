package ru.serbis.okto.node.reps

import java.io.File
import java.nio.file.{Files, StandardOpenOption}

import akka.actor.{Actor, Props, Stash}
import akka.pattern.pipe
import akka.util.ByteString
import com.typesafe.config.{Config, ConfigFactory, ConfigObject}
import ru.serbis.okto.node.common.ReachTypes.ReachList
import ru.serbis.okto.node.log.Logger.LogEntryQualifier
import ru.serbis.okto.node.log.StreamLogger
import ru.serbis.okto.node.proxy.files.FilesProxy

import scala.annotation.tailrec
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.{Random, Try}
import scala.collection.JavaConverters._

/** The repository provides thread-safe operations on the boot.conf file. This file uses the configuration format of
  * the lightbend config library. The configuration is downloaded one-time in asynchronous mode on the first request */
object BootRep {

  /** @param confName path to the configuration file */
  def props(confName: String, testMode: Boolean = false) = Props(new BootRep(confName, testMode))

  object Commands {

    /** Return all boot definitions as List[BootDefinition]*/
    case object GetAll

    /** Create new boot definition. Respond with BootDefinition
      *
      * @param cmd command line
      */
    case class Create(cmd: String)

    /** Remove boot definition by it's id
      *
      * @param id boot line definition
      */
    case class Remove(id: Int)

    /** Drop current configuration cache. At next command the configuration will be read from the disk */
    case object DropCache
  }

  object Responses {

    /** Description of the boot command
      *
      * @param id boot line id
      * @param cmd command line
      */
    case class BootDefinition(id: Int, cmd: String)

    /** Configuration file io error at writing operation */
    case object WriteError

    /** Response for Remove command */
    case object Removed

    /** Definition not exist in the configuration */
    case object NotExist
  }

  object Internals {

    /** This message is intended for asynchronous loading of the configuration. See the comment to the function withConf.
      *
      * @param config loaded configuration
      */
    case class ConfigLoaded(config: Config)
  }
}

class BootRep(confName: String, testMode: Boolean) extends Actor with StreamLogger with Stash {
  import BootRep.Commands._
  import BootRep.Internals._
  import BootRep.Responses._

  setLogSourceName(s"BootRep*${self.path.name}")
  setLogKeys(Seq("BootRep"))

  implicit val logQualifier = LogEntryQualifier("static")

  /** Current configuration instance */
  var config: Option[Config] = None

  /** Configuration file */
  val configFile = new File(confName)

  /** Flag meaning to work with the configuration file in asynchronous mode */
  var fileOperation = false

  logger.info("BootRep actor is initialized")

  override def receive = {

    /** see the message description */
    case GetAll =>
      implicit val logQualifier = LogEntryQualifier("GetAll")

      withConf {

        val objs = config.get.root().asScala.toList
        val r = objs map {v =>
          try {
            val d = BootDefinition(v._1.toInt, config.get.getObject(v._1).get("cmd").unwrapped().asInstanceOf[String])
            logger.debug(s"Boot definition $d was read from the configuration")
            Some(d)
          } catch {
            case ex: Exception =>
              logger.debug(s"Error reading boot definitions from the configuration. Error reason: ${ex.getMessage}")
              None
          }
        } filter {
          v => v.isDefined
        } map {v =>
          v.get
        }

        sender() ! r
      }

    /** see the message description */
    case Create(cmd) =>
      implicit val logQualifier = LogEntryQualifier("Create")

      withConf {
        if (fileOperation) {
          stash()
        } else {
          val orig = sender()
          fileOperation = true
          Future {
            try {
              if (testMode) Thread.sleep(2000)
              val id = newId()
              Files.write(configFile.toPath, ByteString(s"$id {\n\tcmd: ${"\""}$cmd${"\""}\n}\n").toArray, StandardOpenOption.APPEND)
              orig ! BootDefinition(id, cmd)
              fileOperation = false
              logger.debug(s"Created new boot definition for command '$cmd'")
              DropCache
            } catch {
              case e: Exception =>
                orig ! WriteError
                fileOperation = false
                logger.error(s"IO error while creating new boot definition for command '$cmd'. Reason: ${e.getMessage}")
            }
          } pipeTo self
        }
      }

    /** see the message description */
    case Remove(id) =>
      implicit val logQualifier = LogEntryQualifier("Remove")

      withConf {
        if (fileOperation) {
          stash()
          println("Stashed")
        } else {
          if (config.get.hasPath(s"$id") || config.get.hasPath(s""""$id\"""")) {
            val nConf = config.get.root().asScala.foldLeft("")((a, v) => {
              if (v._1 != id.toString)
                s"$a${v._1} {\n\tcmd: ${"\""}${v._2.asInstanceOf[ConfigObject].get("cmd").unwrapped()}${"\""}\n}\n"
              else
                a
            })
            val orig = sender()
            fileOperation = true
            Future {
              try {
                if (testMode) Thread.sleep(2000)
                Files.write(configFile.toPath, ByteString(nConf).toArray, StandardOpenOption.TRUNCATE_EXISTING)
                orig ! Removed
                fileOperation = false
                logger.info(s"Removed boot definition for id '$id'")
                DropCache
              } catch {
                case e: Throwable =>
                  orig ! WriteError
                  fileOperation = false
                  logger.error(s"IO error while removing boot definition for id '$id'. Reason: ${e.getMessage}")
              }
            } pipeTo self
          } else {
            logger.debug(s"Unable to remove boot definition for id '$id'. Definition does not exist")
            sender() ! NotExist
          }
        }
      }


    /** see the message description */
    case DropCache =>
      implicit val logQualifier = LogEntryQualifier("DropCache")

      config = None
      unstashAll()
      logger.debug("Configuration cache was dropped")

    /** see the message description */
    case ConfigLoaded(conf) =>
      config = Some(conf)
      unstashAll()
  }

  /** This function executes the body of the message, pre-checking the availability of the configuration object. If
    *  the configuration was not previously loaded from the disk, the function performs a stash of the current message
    *  and load the configuration from the disk via the future. When the future finishes, it sends to the self,
    *  ConfigurationLoaded message, in which hsnler the created configuration object is assigned and the messages is
    *  unstashed.
    *
    * @param f message handler body
    */
  def withConf(f: => Unit) = {
    if (config.isEmpty) {
      stash()
      pipe (Future {
        if (!Files.exists(configFile.toPath))
          logger.warning(s"The configuration '$confName' was not found in the path")
        ConfigLoaded(ConfigFactory.parseFile(configFile)) }) to self
    } else {
      f
    }
  }

  /** Find not used id for new boot entry */
  def newId(): Int = {

    @tailrec
    def rec(l: List[Int], r: Int): Int = {
      if (!l.contains(r)) r
      else rec(l, Random.nextInt(Int.MaxValue))
    }

    val ojbs = config.get.root().asScala.toList
    val ids = ojbs map {v =>
      try {
        Some(v._1.toInt)
      } catch { case _: Exception => None }
    } filter {
      v => v.isDefined
    } map {v =>
      v.get
    }

    if (testMode) 4
    else rec(ids, Random.nextInt(Int.MaxValue))
  }
}