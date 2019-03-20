package ru.serbis.okto.node.reps

import java.io.File
import java.nio.file.{Files, StandardOpenOption}
import java.util

import akka.actor.{Actor, Props, Stash}
import akka.pattern.pipe
import akka.util.ByteString
import com.typesafe.config.{Config, ConfigFactory, ConfigList, ConfigObject}
import ru.serbis.okto.node.common.ReachTypes.ReachList
import ru.serbis.okto.node.log.Logger.LogEntryQualifier
import ru.serbis.okto.node.log.StreamLogger
import ru.serbis.okto.node.reps.UsercomsRep.Responses.UserCommandDefinition

import collection.JavaConverters._
import scala.annotation.tailrec
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Try

/** The repository provides thread-safe operations on the usercoms.conf file. This file uses the configuration format of
  * the lightbend config library. The configuration is downloaded one-time in asynchronous mode on the first request */
object UsercomsRep {

  /** @param confName path to the configuration file */
  def props(confName: String, testMode: Boolean = false) =
    Props(new UsercomsRep(confName, testMode))

  object Commands {

    /** Returns a package of command descriptions with names from the attached list. Responds with a CommandsBatch
      * message with descriptions. The peculiarity of this message is the fact that in the absence of a command in
      * the configuration, instead of describing it, None is returned.
      *
      * @param commands list of names of extracted commands
      */
    case class GetCommandsBatch(commands: List[String])

    /** Add a new command definition to the configuration. After writing to the file, the configuration cache will be
      * updated. Respond with message Created if operation was success, Exist if definition already exist in the
      * configuration or WriteError if file io error was occurred.
      *
      * @param definition command definition
      */
    case class Create(definition: UserCommandDefinition)

    /** Remove a command definition from the configuration. After writing to the file, the configuration cache will be
      * updated. Respond with message Removed if operation was success, NotExist if definition does not exist in the
      * configuration or WriteError if file io error was occurred.
      *
      * @param cmd command name
      */
    case class Remove(cmd: String)

    /** Drop current configuration cache. At next command the configuration will be read from the disk */
    case object DropCache
  }

  object Responses {

    /** Description of the configuration properties of a command
      *
      * @param name command name
      * @param file a file that implements the logic of a command
      */
    case class UserCommandDefinition(name: String, file: String, users: Vector[String] = Vector.empty, groups: Vector[String] = Vector.empty)

    /** Reply to the message GetCommandsBatch. It is an associative array, where the keys are the names of the
      * requested commands, and the values are optional values. The optional value can contain either the definition
      * of the command, or it will have the value None if the command under the specified name was not found in the
      * configuration.
      *
      * @param commandsDef definitions batch
      */
    case class CommandsBatch(commandsDef: Map[String, Option[UserCommandDefinition]])

    /** Response for Create command */
    case object Created

    /** Response for Removed command*/
    case object Removed

    /** Configuration file io error at writing operation */
    case object WriteError

    /** Command exist in the configuration */
    case object Exist

    /** Command not exist in the configuration */
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

class UsercomsRep(confName: String, testMode: Boolean) extends Actor with StreamLogger with Stash {
  import UsercomsRep.Commands._
  import UsercomsRep.Internals._
  import UsercomsRep.Responses._

  setLogSourceName(s"UsercomsRep*${self.path.name}")
  setLogKeys(Seq("UsercomsRep"))

  implicit val logQualifier = LogEntryQualifier("static")

  /** Current configuration instance */
  var config: Option[Config] = None

  /** Configuration file */
  val configFile = new File(confName)


  /** Flag meaning to work with the configuration file in asynchronous mode */
  var fileOperation = false

  logger.info("UsercomsRep actor is initialized")


  override def receive = {

    /** see the message description */
    case Remove(cmd) =>
      implicit val logQualifier = LogEntryQualifier("Remove")

      withConf {
        if (fileOperation) {
          stash()
        } else {
          if (config.get.hasPath(s"$cmd") || config.get.hasPath(s""""$cmd\"""")) {
            val nConf = config.get.root().asScala.foldLeft("")((a, v) => {
              if (v._1 != cmd) {
                val file = v._2.asInstanceOf[ConfigObject].get("file").unwrapped()
                val users = try {
                  v._2.asInstanceOf[ConfigObject].get("users").asInstanceOf[ConfigList].unwrapped().asInstanceOf[util.ArrayList[String]].asScala.toVector
                } catch { case _: Throwable => Vector.empty }
                val groups = try {
                  v._2.asInstanceOf[ConfigObject].get("groups").asInstanceOf[ConfigList].unwrapped().asInstanceOf[util.ArrayList[String]].asScala.toVector
                } catch { case _: Throwable => Vector.empty }
                val usersStr = users.foldLeft("")((a, v) => s"$a${"\""}$v${"\""}, ").dropRight(2)
                val groupsStr = groups.foldLeft("")((a, v) => s"$a${"\""}$v${"\""}, ").dropRight(2)

                s"$a${"\""}${v._1}${"\""} {\n  file = ${"\""}${file}${"\""}\n  users = [$usersStr]\n  groups = [$groupsStr]\n}\n"
                //s"$a${"\""}${v._1}${"\""} {\n  file = ${"\""}${v._2.asInstanceOf[ConfigObject].get("file").unwrapped()}${"\""}\n}\n"
              } else
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
                logger.info(s"Removed definition for command '$cmd'")
                DropCache
              } catch {
                case e: Throwable =>
                  orig ! WriteError
                  fileOperation = false
                  logger.error(s"IO error while removing definition for command '$cmd'. Reason: ${e.getMessage}")
              }
            } pipeTo self
          } else {
            logger.debug(s"Unable to remove definition for command '$cmd'. Definition does not exist")
            sender() ! NotExist
          }
        }
      }

    /** see the message description */
    case GetCommandsBatch(commands) =>
      implicit val logQualifier = LogEntryQualifier("GetCommandsBatch")

      @tailrec
      def rec(in: List[String], out: Map[String, Option[UserCommandDefinition]]): Map[String, Option[UserCommandDefinition]] = {
        if (in.isEmpty) {
          out
        } else {
            val inName = in.head
            val result = Try {
              val file = config.get.getString(s"${"\""}$inName${"\""}.file")
              val users = try {
                config.get.getAnyRef(s"${"\""}$inName${"\""}.users").asInstanceOf[util.ArrayList[String]].asScala.toVector
              } catch {
                case e: Throwable =>
                  logger.error(s"Unable to read users list [name=$inName, reason=${e.getMessage}]")
                  Vector.empty
              }
              val groups = try {
                config.get.getAnyRef(s"${"\""}$inName${"\""}.groups").asInstanceOf[util.ArrayList[String]].asScala.toVector
              } catch {
                case e: Throwable =>
                  logger.error(s"Unable to read groups list [name=$inName, reason=${e.getMessage}]")
                  Vector.empty
              }
              val definition = UserCommandDefinition(inName, file, users, groups)
              logger.debug(s"System command definition $definition was read from the configuration")
              Some(definition)
            } recover {
              case r =>
                logger.debug(s"Error reading user command definition '$inName' from the configuration. Error reason: ${r.getMessage}")
                None
            }

            rec(in.tailOrEmpty, out + (inName -> result.get))
        }
      }

      withConf {
        if (commands.nonEmpty) {
          sender() ! CommandsBatch(rec(commands, Map.empty))
        } else {
          val batch = CommandsBatch(config.get.root().asScala.foldLeft(Map.empty[String, Option[UserCommandDefinition]])((a, v) => {
            try {
              val file = v._2.asInstanceOf[ConfigObject].get("file").unwrapped().toString
              val users = try {
                v._2.asInstanceOf[ConfigObject].get("users").asInstanceOf[ConfigList].unwrapped().asInstanceOf[util.ArrayList[String]].asScala.toVector
              } catch { case _: Throwable => Vector.empty }
              val groups = try {
                v._2.asInstanceOf[ConfigObject].get("groups").asInstanceOf[ConfigList].unwrapped().asInstanceOf[util.ArrayList[String]].asScala.toVector
              } catch { case _: Throwable => Vector.empty }
              a + (v._1 -> Some(UserCommandDefinition(v._1, file, users, groups)))
            } catch {
              case e: Throwable =>
                logger.warning(s"Fragmentary error while create command list at command '${v._1}'. Reason: '${e.getMessage}'")
                a
            }
          }))

          logger.debug("Full command list was requested")
          sender() ! batch
        }
      }

    /** see the message description */
    case Create(df) =>
      implicit val logQualifier = LogEntryQualifier("Create")

      withConf {
        if (fileOperation) {
          stash()
        } else {
          if (!config.get.hasPath(s"${df.name}")) {
            val orig = sender()
            fileOperation = true
            Future {
              try {
                if (testMode) Thread.sleep(2000)
                val users = df.users.foldLeft("")((a, v) => s"$a${"\""}$v${"\""}, ").dropRight(2)
                val groups = df.groups.foldLeft("")((a, v) => s"$a${"\""}$v${"\""}, ").dropRight(2)
                Files.write(configFile.toPath, ByteString(s"${"\""}${df.name}${"\""} {\n  file = ${"\""}${df.file}${"\""}\n  users = [$users]\n  groups = [$groups]\n}\n").toArray, StandardOpenOption.APPEND)
                orig ! Created
                fileOperation = false
                logger.info(s"Created new definition for command '${df.name}'")
                DropCache
              } catch {
                case e: Exception =>
                  orig ! WriteError
                  fileOperation = false
                  logger.error(s"IO error while creating new definition for command '${df.name}'. Reason: ${e.getMessage}")
              }
            } pipeTo self
          } else {
            logger.debug(s"Unable to create new command definition for command '${df.name}'. Definition already exist")
            sender() ! Exist
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
}