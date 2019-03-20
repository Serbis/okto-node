package ru.serbis.okto.node.reps

import java.io.File
import java.nio.file.Files

import akka.actor.{Actor, Props, Stash}
import akka.pattern.pipe
import com.typesafe.config.{Config, ConfigFactory}
import ru.serbis.okto.node.log.Logger.LogEntryQualifier
import ru.serbis.okto.node.log.StreamLogger

import scala.annotation.tailrec
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.util.Try
import ru.serbis.okto.node.common.ReachTypes.ReachList


/** The repository provides thread-safe operations on the syscoms.conf file. This file uses the configuration format of
  * the lightbend config library. The configuration is downloaded one-time in asynchronous mode on the first request */
object SyscomsRep {
  /** @param confName path to the configuration file */
  def props(confName: String) = Props(new SyscomsRep(confName))

  object Commands {

    /** Returns a package of command descriptions with names from the attached list. Responds with a CommandsBatch
      * message with descriptions. The peculiarity of this message is the fact that in the absence of a command in
      * the configuration, instead of describing it, None is returned.
      *
      * @param commands list of names of extracted commands
      */
    case class GetCommandsBatch(commands: List[String])
  }

  object Responses {

    /** Description of the configuration properties of a command
      *
      * @param clazz a class that implements the logic of a command
      */
    case class SystemCommandDefinition(clazz: String)

    /** Reply to the message GetCommandsBatch. It is an associative array, where the keys are the names of the
      * requested commands, and the values are optional values. The optional value can contain either the definition
      * of the command, or it will have the value None if the command under the specified name was not found in the
      * configuration.
      *
      * @param commandsDef definitions batch
      */
    case class CommandsBatch(commandsDef: Map[String, Option[SystemCommandDefinition]])
  }

  object Internals {

    /** This message is intended for asynchronous loading of the configuration. See the comment to the function withConf.
      *
      * @param config loaded configuration
      */
    case class ConfigLoaded(config: Config)
  }
}

class SyscomsRep(confName: String) extends Actor with StreamLogger with Stash {
  import SyscomsRep.Commands._
  import SyscomsRep.Internals._
  import SyscomsRep.Responses._

  setLogSourceName(s"SyscomsRep*${self.path.name}")
  setLogKeys(Seq("SyscomsRep"))

  implicit val logQualifier = LogEntryQualifier("static")

  /** Current configuration instance */
  var config: Option[Config] = None

  logger.info("SyscomsRep actor is initialized")

  override def receive = {

    /** see the message description */
    case GetCommandsBatch(commands) =>
      implicit val logQualifier = LogEntryQualifier("GetCommandsBatch")

      @tailrec
      def rec(in: List[String], out: Map[String, Option[SystemCommandDefinition]]): Map[String, Option[SystemCommandDefinition]] = {
        if (in.isEmpty) {
          out
        } else {
            val inName = in.head
            val result = Try {
              val definition = SystemCommandDefinition(config.get.getString(s"$inName.class"))
              logger.debug(s"System command definition $definition was read from the configuration")
              Some(definition)
            } recover {
              case r =>
                logger.debug(s"Error reading system command definition '$inName' from the configuration. Error reason: ${r.getMessage}")
                None
            }

            rec(in.tailOrEmpty, out + (inName -> result.get))
        }
      }

      withConf {
        sender() ! CommandsBatch(rec(commands, Map.empty))
      }

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
        val file = new File(confName)
        if (!Files.exists(file.toPath))
          logger.warning(s"The configuration '$confName' was not found in the path")
        ConfigLoaded(ConfigFactory.parseFile(file)) }) to self
    } else {
      f
    }
  }
}