package ru.serbis.okto.node.reps

import java.io.File
import java.nio.file.{Files, LinkOption, Path, Paths}

import akka.actor.{Actor, Props, Stash}
import akka.pattern.pipe
import com.typesafe.config.{Config, ConfigFactory}
import ru.serbis.okto.node.log.Logger.LogEntryQualifier
import ru.serbis.okto.node.log.{Logger, StreamLogger}
import shapeless.Path

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import collection.JavaConverters._
import scala.util.Try


/** The repository provides thread-safe operations on the main.conf file. This file uses the configuration format of
  * the lightbend config library. The configuration is downloaded one-time in asynchronous mode on the first request */
object MainRep {

  /** @param confName path to the configuration file */
  def props(confName: String) = Props(new MainRep(confName))

  object Commands {

    /** Requests a group of parameters related to the node's logging system. The result of this command is the
      * LogConfiguration response. All configuration parameters for this block are not fatal and, if they are not
      * present in the configuration file, they are replaced by default values. */
    case object GetLogConfiguration

    /** Requests a group of parameters related to the node's shell. The result of this command is the
      * ShellConfiguration response. All configuration parameters for this block are not fatal and, if they are not
      * present in the configuration file, they are replaced by default values. */
    case object GetShellConfiguration

    /** Requests a group of parameters related to the node's expansion board uart port. The result of this command is
      * the UartConfiguration response. All configuration parameters for this block are not fatal and, if they are not
      * present in the configuration file, they are replaced by default values. */
    case object GetHardwareConfiguration //FIXME comment

    case object GetVirtualizationConfiguration

    case object GetAllConfiguration
  }

  object Responses {
    /** Response for GetLogLevel message */
    case class LogLevel(level: Logger.LogLevels.LogLevel)
    /** Response for GetLogKeys message */
    case class LogKeys(keys: Seq[String])

    /** Response for GetLogConfiguration message */
    case class LogConfiguration(
      level: Logger.LogLevels.LogLevel,
      keys: Seq[String],
      path: java.nio.file.Path,
      truncate: Boolean
    )

    /** Response for GetShellConfiguration message */
    case class ShellConfiguration(
      host: String,
      port: Int,
      keystoreFile: String,
      keystorePass: String
    )

    /** Response for GetUartConfiguration message */
    case class HardwareConfiguration(
      emulation:  Boolean,
      uartConfiguration: UartConfiguration,
      nsdConfiguration: NsdConfiguration,
      rfConfiguration: RfConfiguration
    )

    case class UartConfiguration(
      device: String,
      baud: Int,
      responseCleanInterval: Int,
      maxReq: Int
    )

    case class NsdConfiguration(
      socket: String,
      responseCleanInterval: Int,
      maxReq: Int
    )

    case class RfConfiguration(
      socket: String,
      responseCleanInterval: Int,
      maxReq: Int,
      p1_targ: String,
      p1_self: String,
      p2_targ: String,
      p2_self: String,
      p3_targ: String,
      p3_self: String,
      p4_targ: String,
      p4_self: String,
      p5_targ: String,
      p5_self: String,
    )

    case class VirtualizationConfiguration(
      minVm: Int,
      maxVm: Int,
      scriptCacheTime: Int,
      scriptCacheCleanInterval: Int
    )

    case class StorageConfiguration(
      path: String
    )

    case class FullConfiguration(
      logConfiguration: LogConfiguration,
      shellConfiguration: ShellConfiguration,
      hardwareConfiguration: HardwareConfiguration,
      virtualizationConfiguration: VirtualizationConfiguration,
      storageConfiguration: StorageConfiguration
    )
  }

  object Internals {

    /** This message is intended for asynchronous loading of the configuration. See the comment to the function withConf.
      *
      * @param config loaded configuration
      */
    case class ConfigLoaded(config: Config)
  }
}

class MainRep(confName: String) extends Actor with StreamLogger with Stash {
  import MainRep.Commands._
  import MainRep.Internals._
  import MainRep.Responses._

  setLogSourceName(s"MainRep*${self.path.name}")
  setLogKeys(Seq("MainRep"))

  implicit val logQualifier = LogEntryQualifier("static")

  /** Current configuration instance */
  var config: Option[Config] = None

  logger.info("MainRep actor is initialized")

  override def receive = {

    /** see the message description */
    case GetLogConfiguration =>
      withConf {
        sender() ! getLogConfiguration
      }

    /** see the message description */
    case GetShellConfiguration =>
      withConf {
        sender() ! getShellConfiguration
      }

    /** see the message description */
    case GetHardwareConfiguration =>
      withConf {
        sender() ! getHardwareConfiguration
      }

    case GetAllConfiguration =>
      withConf {
        sender() ! FullConfiguration(
          getLogConfiguration,
          getShellConfiguration,
          getHardwareConfiguration,
          getVirtualizationConfiguration,
          getStorageConfiguration
        )
      }

    /** see the message description */
    case ConfigLoaded(conf) =>
      config = Some(conf)
      unstashAll()
  }

  def getVirtualizationConfiguration = {
    val maxVm = Try {
      config.get.getInt("node.virtualization.maxVm")
    } recover {
      case r =>
        logger.warning(s"Error reading configuration parameter node-> virtualization -> maxVm. The default value of '30' is returned. Error reason: ${r.getMessage}")
        30
    }

    val minVm = Try {
      config.get.getInt("node.virtualization.minVm")
    } recover {
      case r =>
        logger.warning(s"Error reading configuration parameter node-> virtualization -> minVm. The default value of '3' is returned. Error reason: ${r.getMessage}")
        3
    }

    val scriptCacheTime = Try {
      config.get.getInt("node.virtualization.scriptCacheTime")
    } recover {
      case r =>
        logger.warning(s"Error reading configuration parameter node-> virtualization -> scriptCacheTime. The default value of '60000' is returned. Error reason: ${r.getMessage}")
        60000
    }

    val scriptCacheCleanInterval = Try {
      config.get.getInt("node.virtualization.scriptCacheCleanInterval")
    } recover {
      case r =>
        logger.warning(s"Error reading configuration parameter node-> virtualization -> scriptCacheCleanInterval. The default value of '30000' is returned. Error reason: ${r.getMessage}")
        30000
    }

    VirtualizationConfiguration(minVm.get, maxVm.get, scriptCacheTime.get, scriptCacheCleanInterval.get)
  }

  def getLogConfiguration = {
    val level = Try {
      config.get.getString("node.log.level") match {
        case "DEBUG" => Logger.LogLevels.Debug
        case "INFO" => Logger.LogLevels.Info
        case "WARNING" => Logger.LogLevels.Warning
        case "ERROR" => Logger.LogLevels.Error
        case "FATAL" => Logger.LogLevels.Fatal
        case _ =>
          logger.warning("The log level parameter 'logLevel' is incorrectly specified, the default value of 'INFO' is returned")
          Logger.LogLevels.Info
      }
    } recover {
      case r =>
        logger.warning(s"Error reading configuration parameter node-> log -> level. The default value of INFO is returned. Error reason: ${r.getMessage}")
        Logger.LogLevels.Info
    }

    val keys = Try {
      val keys = config.get.getStringList("node.log.keys")
      keys.asScala
    } recover {
      case r =>
        logger.warning(s"Error reading configuration parameter node-> log -> keys. The default value of empty seq is returned. Error reason: ${r.getMessage}")
        Seq.empty
    }

    val file = Try {
      val path = new File(config.get.getString("node.log.file")).toPath
      path
    } recover {
      case r =>
        logger.warning(s"Error reading configuration parameter node-> log -> file. The default value of '/tmp/node.log' is returned. Error reason: ${r.getMessage}")
        new File("/tmp/node.log").toPath
    }

    val truncate = Try {
      config.get.getBoolean("node.log.fileTruncate")
    } recover {
      case r =>
        logger.warning(s"Error reading configuration parameter node-> log -> fileTruncate. The default value of 'true' is returned. Error reason: ${r.getMessage}")
        true
    }

    LogConfiguration(level.get, keys.get, file.get, truncate.get)
  }


  def getShellConfiguration = {
    val host = Try {
      config.get.getString("node.shell.host")
    } recover {
      case r =>
        logger.warning(s"Error reading configuration parameter node-> shell -> host. The default value of '127.0.0.1' is returned. Error reason: ${r.getMessage}")
        "127.0.0.1"
    }

    val port = Try {
      config.get.getInt("node.shell.port")
    } recover {
      case r =>
        logger.warning(s"Error reading configuration parameter node-> shell -> port. The default value of '5000' is returned. Error reason: ${r.getMessage}")
        5000
    }

    val keystoreFile = Try {
      config.get.getString("node.shell.keystoreFile")
    } recover {
      case r =>
        logger.warning(s"Error reading configuration parameter node-> shell -> keystoreFile. The default value of '/usr/share/node/tls/localhost.p12' is returned. Error reason: ${r.getMessage}")
        "/usr/share/node/tls/localhost.p12"
    }

    val keystorePass = Try {
      config.get.getString("node.shell.keystorePass")
    } recover {
      case r =>
        logger.warning(s"Error reading configuration parameter node-> shell -> keystorePass. The default value of '123456' is returned. Error reason: ${r.getMessage}")
        "123456"
    }

    ShellConfiguration(host.get, port.get, keystoreFile.get, keystorePass.get)
  }

  def getHardwareConfiguration = {
    val bridgeDevice = Try {
      config.get.getString("node.hardware.uart.device")
    } recover {
      case r =>
        logger.warning(s"Error reading configuration parameter node-> hardware -> uart -> device. The default value of '/dev/ttyS1' is returned. Error reason: ${r.getMessage}")
        "/dev/ttyS1"
    }

    val bridgeBaud = Try {
      config.get.getInt("node.hardware.uart.baud")
    } recover {
      case r =>
        logger.warning(s"Error reading configuration parameter node-> hardware -> uart -> baud. The default value of '115200' is returned. Error reason: ${r.getMessage}")
        115200
    }

    val bridgeMaxReq = Try {
      config.get.getInt("node.hardware.uart.maxReq")
    } recover {
      case r =>
        logger.warning(s"Error reading configuration parameter node-> hardware -> uart -> maxReq. The default value of '50' is returned. Error reason: ${r.getMessage}")
        50
    }

    val bridgeResponseCleanInterval = Try {
      config.get.getInt("node.hardware.uart.responseCleanInterval")
    } recover {
      case r =>
        logger.warning(s"Error reading configuration parameter node-> hardware -> uart -> responseCleanInterval. The default value of '1000' is returned. Error reason: ${r.getMessage}")
        1000
    }

    val nsdSocket = Try {
      config.get.getString("node.hardware.nsd.socket")
    } recover {
      case r =>
        logger.warning(s"Error reading configuration parameter node-> hardware -> nsd -> socket. The default value of '/tmp/nsd.socket' is returned. Error reason: ${r.getMessage}")
        "/tmp/nsd.socket"
    }

    val nsdMaxReq = Try {
      config.get.getInt("node.hardware.nsd.maxReq")
    } recover {
      case r =>
        logger.warning(s"Error reading configuration parameter node-> hardware -> nsd -> maxReq. The default value of '50' is returned. Error reason: ${r.getMessage}")
        50
    }

    val nsdResponseCleanInterval = Try {
      config.get.getInt("node.hardware.nsd.responseCleanInterval")
    } recover {
      case r =>
        logger.warning(s"Error reading configuration parameter node-> hardware -> nsd -> responseCleanInterval. The default value of '1000' is returned. Error reason: ${r.getMessage}")
        1000
    }

    val rfSocket = Try {
      config.get.getString("node.hardware.rf.socket")
    } recover {
      case r =>
        logger.warning(s"Error reading configuration parameter node-> hardware -> rf -> socket. The default value of '/tmp/wsd.socket' is returned. Error reason: ${r.getMessage}")
        "/tmp/wsd.socket"
    }

    val rfMaxReq = Try {
      config.get.getInt("node.hardware.rf.maxReq")
    } recover {
      case r =>
        logger.warning(s"Error reading configuration parameter node-> hardware -> rf -> maxReq. The default value of '50' is returned. Error reason: ${r.getMessage}")
        50
    }

    val rfResponseCleanInterval = Try {
      config.get.getInt("node.hardware.rf.responseCleanInterval")
    } recover {
      case r =>
        logger.warning(s"Error reading configuration parameter node-> hardware -> rf -> responseCleanInterval. The default value of '1000' is returned. Error reason: ${r.getMessage}")
        1000
    }

    //-------------

    val rfP1Targ = Try {
      config.get.getString("node.hardware.rf.p1_targ")
    } recover {
      case r =>
        logger.warning(s"Error reading configuration parameter node-> hardware -> rf -> p1_targ. The default value of 'AAAAAA01' is returned. Error reason: ${r.getMessage}")
        "AAAAAA01"
    }

    val rfP1Self = Try {
      config.get.getString("node.hardware.rf.p1_self")
    } recover {
      case r =>
        logger.warning(s"Error reading configuration parameter node-> hardware -> rf -> p1_self. The default value of 'AAAAAA11' is returned. Error reason: ${r.getMessage}")
        "AAAAAA11"
    }



    val rfP2Targ = Try {
      config.get.getString("node.hardware.rf.p2_targ")
    } recover {
      case r =>
        logger.warning(s"Error reading configuration parameter node-> hardware -> rf -> p2_targ. The default value of 'AAAAAA02' is returned. Error reason: ${r.getMessage}")
        "AAAAAA02"
    }

    val rfP2Self = Try {
      config.get.getString("node.hardware.rf.p2_self")
    } recover {
      case r =>
        logger.warning(s"Error reading configuration parameter node-> hardware -> rf -> p2_self. The default value of 'AAAAAA12' is returned. Error reason: ${r.getMessage}")
        "AAAAAA12"
    }


    val rfP3Targ = Try {
      config.get.getString("node.hardware.rf.p3_targ")
    } recover {
      case r =>
        logger.warning(s"Error reading configuration parameter node-> hardware -> rf -> p3_targ. The default value of 'AAAAAA03' is returned. Error reason: ${r.getMessage}")
        "AAAAAA03"
    }

    val rfP3Self = Try {
      config.get.getString("node.hardware.rf.p3_self")
    } recover {
      case r =>
        logger.warning(s"Error reading configuration parameter node-> hardware -> rf -> p3_self. The default value of 'AAAAAA13' is returned. Error reason: ${r.getMessage}")
        "AAAAAA13"
    }


    val rfP4Targ = Try {
      config.get.getString("node.hardware.rf.p4_targ")
    } recover {
      case r =>
        logger.warning(s"Error reading configuration parameter node-> hardware -> rf -> p4_targ. The default value of 'AAAAAA04' is returned. Error reason: ${r.getMessage}")
        "AAAAAA04"
    }

    val rfP4Self = Try {
      config.get.getString("node.hardware.rf.p4_self")
    } recover {
      case r =>
        logger.warning(s"Error reading configuration parameter node-> hardware -> rf -> p4_self. The default value of 'AAAAAA14' is returned. Error reason: ${r.getMessage}")
        "AAAAAA14"
    }


    val rfP5Targ = Try {
      config.get.getString("node.hardware.rf.p5_targ")
    } recover {
      case r =>
        logger.warning(s"Error reading configuration parameter node-> hardware -> rf -> p5_targ. The default value of 'AAAAAA05' is returned. Error reason: ${r.getMessage}")
        "AAAAAA05"
    }

    val rfP5Self = Try {
      config.get.getString("node.hardware.rf.p5_self")
    } recover {
      case r =>
        logger.warning(s"Error reading configuration parameter node-> hardware -> rf -> p5_self. The default value of 'AAAAAA15' is returned. Error reason: ${r.getMessage}")
        "AAAAAA15"
    }

    //---------------

    val emulation = Try {
      config.get.getBoolean("node.hardware.emulation")
    } recover {
      case r =>
        logger.warning(s"Error reading configuration parameter node-> hardware -> emulation. The default value of 'false' is returned. Error reason: ${r.getMessage}")
        false
    }
    val uartConfiguration = UartConfiguration(bridgeDevice.get, bridgeBaud.get, bridgeResponseCleanInterval.get, bridgeMaxReq.get)
    val nsdConfiguration = NsdConfiguration(nsdSocket.get, nsdResponseCleanInterval.get, nsdMaxReq.get)
    val rfConfiguration = RfConfiguration(
      rfSocket.get,
      rfResponseCleanInterval.get,
      rfMaxReq.get,
      rfP1Targ.get,
      rfP1Self.get,
      rfP2Targ.get,
      rfP2Self.get,
      rfP3Targ.get,
      rfP3Self.get,
      rfP4Targ.get,
      rfP4Self.get,
      rfP5Targ.get,
      rfP5Self.get
    )
    HardwareConfiguration(emulation.get, uartConfiguration, nsdConfiguration, rfConfiguration)
  }

  def getStorageConfiguration = {
    val storagePath = Try {
      config.get.getString("node.storage.path")
    } recover {
      case r =>
        logger.warning(s"Error reading configuration parameter node-> storage -> path. The default value of '/usr/share/node/storage' is returned. Error reason: ${r.getMessage}")
        "/usr/share/node/storage"
    }


    StorageConfiguration(storagePath.get)
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
