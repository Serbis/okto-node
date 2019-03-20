package ru.serbis.okto.node

import java.io.{File, FileInputStream, InputStream}
import java.math.BigInteger
import java.nio.file.Files
import java.security.{KeyStore, SecureRandom}
import java.util.concurrent.TimeUnit

import akka.actor.{ActorRef, FSM, Props, Status}
import akka.event.Logging
import akka.http.scaladsl.{ConnectionContext, Http, HttpsConnectionContext}
import akka.http.scaladsl.Http.ServerBinding
import akka.stream.ActorMaterializer
import javax.net.ssl.{KeyManagerFactory, SSLContext, TrustManagerFactory}
import ru.serbis.okto.node.access.AccessRep
import ru.serbis.okto.node.adapter.RestLayer
import ru.serbis.okto.node.boot.BootManager
import ru.serbis.okto.node.common.Env
import ru.serbis.okto.node.common.FsmDefaults.{Data, State}
import ru.serbis.okto.node.events.Eventer
import ru.serbis.okto.node.hardware._
import ru.serbis.okto.node.log.Logger.{LogEntryQualifier, LogLevels}
import ru.serbis.okto.node.log.{FileLogger, StdOutLogger, StreamLogger}
import ru.serbis.okto.node.proxy.files.RealFilesProxy
import ru.serbis.okto.node.proxy.napi.RealNativeApiProxy
import ru.serbis.okto.node.proxy.system.RealActorSystemProxy
import ru.serbis.okto.node.reps._
import ru.serbis.okto.node.runtime.{Runtime, VmPool}

import scala.concurrent.duration._

/** This actor performs initial initialization of the node. */
object Runner {

  def props: Props = Props(new Runner)

  object States {
    /** see description of the state code */
    case object Idle extends State

    /** see description of the state code */
    case object Configure extends State

    /*/** see description of the state code */
    case object ConfigureUart extends State

    /** see description of the state code */
    case object ConfigureVirtualization extends State

    /** see description of the state code */
    case object ConfigureRest extends State*/

    /** see description of the state code */
    case object WaitShellEndpointBinding extends State
  }

  object StatesData {
    /** n/c */
    case object Uninitialized extends Data

    /** @param workDir node's work directory
      */
    case class Configuring(workDir: String, mainRep: ActorRef) extends Data

    /*/** @param workDir node's work directory
      * @param conf repository object
      */
    case class ConfiguringUart(workDir: String, conf: Conf) extends Data

    /** @param workDir node's work directory
      * @param conf repository object
      */
    case class ConfiguringVirtualization(workDir: String, conf: Conf) extends Data

    /** @param workDir node's work directory
      * @param conf repository object
      * @param uartConfiguration uart configuration
      */
    case class ConfiguringRest(workDir: String, conf: Conf, uartConfiguration: UartConfiguration) extends Data*/

    /** N/A */
    case class WaitingShellEndpointBinding() extends Data
  }

  object Commands {

    /** @param workDir node's work directory*/
    case class Exec(workDir: String)
  }
}

class Runner extends FSM[State, Data] with StreamLogger {
  import Runner.Commands._
  import Runner.States._
  import Runner.StatesData._

  initializeGlobalLogger(context.system, LogLevels.Info)
  logger.addDestination(context.system.actorOf(StdOutLogger.props, "StdOutLogger"))
  setLogSourceName(s"Runner*${self.path.name}")
  setLogKeys(Seq("Runner"))

  implicit val logQualifier = LogEntryQualifier("static")

  startWith(Idle, Uninitialized)

  /** Starting state. It initializes the repository block. Then, in the repository of the main.conf file, a request is
    * sent for the configuration of the logging system. The timeout for the Exec message is a fatal error that stops
    * the program.
    */
  when(Idle, 30 second) {
    case Event(req: Exec, _) =>
      implicit val logQualifier = LogEntryQualifier("Idle_Exec")

      //Set global log level
      context.system.eventStream.setLogLevel(Logging.ErrorLevel)

      val mainRep = context.system.actorOf(MainRep.props(s"${req.workDir}/node.conf"), "MainRep")
      mainRep ! MainRep.Commands.GetAllConfiguration
      goto(Configure) using Configuring(req.workDir, mainRep)

    case Event(StateTimeout, _) =>
      implicit val logQualifier = LogEntryQualifier("Idle_StateTimeout")
      logger.fatal("Exec command wait timeout")
      context.system.terminate()
      stop
  }

  //TODO comment
  when(Configure, 30 second) {
    case Event(cfg: MainRep.Responses.FullConfiguration, data: Configuring) =>
      implicit val logQualifier = LogEntryQualifier("Configure_FullConfiguration")

      //--- logging system

      logger.info(s"Set global log level to '${cfg.logConfiguration.level}'")
      setGlobalLogLevel(cfg.logConfiguration.level)
      logger.info(s"Set global log keys to ' ${cfg.logConfiguration.keys.foldLeft("")((a, v) => a + s"$v ")}'")
      setGlobalLogKeys(cfg.logConfiguration.keys)
      logger.info(s"Set log file path to '${cfg.logConfiguration.path}'")
      logger.info(s"Set log file truncating to '${cfg.logConfiguration.truncate}'")
      logger.addDestination(context.system.actorOf(FileLogger.props(cfg.logConfiguration.path, cfg.logConfiguration.truncate), "FileLogger"))
      logger.info(s"--------------------------------------")

      //--- jni
      logger.info(s"Set native library emulation mode to '${cfg.hardwareConfiguration.emulation}'")
      if (cfg.hardwareConfiguration.emulation) {
        System.loadLibrary("Hw_x86")
        logger.info("Loaded native library libHw_x86")
      } else {
        System.loadLibrary("Hw")
        logger.info("Loaded native library libHw")
      }

      //--- eventer

      val eventer = context.system.actorOf(Eventer.props(), "Eventer")

      //--- bridges cmd replicator
      val replMatrix = cfg.hardwareConfiguration.cmdRecover.map(v => {
        val addr = if (v.head == "*") None else Some( new BigInteger(v.head, 16).intValue())
        CmdReplicator.Supply.ReplicationPair(addr, v(1))
      })
      logger.info(s"Set command replication matrix to '$replMatrix'")
      val cmdReplicator = context.system.actorOf(CmdReplicator.props(replMatrix, eventer))

      //--- uart

      logger.info(s"Set expansion board uart device to '${cfg.hardwareConfiguration.uartConfiguration.device}'")
      logger.info(s"Set expansion board uart baud rate to '${cfg.hardwareConfiguration.uartConfiguration.baud}'")

      val serialBridge = context.system.actorOf(SerialBridge.props(
        cfg.hardwareConfiguration.uartConfiguration.device,
        cfg.hardwareConfiguration.uartConfiguration.baud,
        cfg.hardwareConfiguration.uartConfiguration.maxReq,
        FiniteDuration(cfg.hardwareConfiguration.uartConfiguration.responseCleanInterval, TimeUnit.MILLISECONDS),
        new RealNativeApiProxy(),
        new RealActorSystemProxy(context.system),
        eventer,
        cmdReplicator), "SerialBridge"
      )

      //--- nsd

      logger.info(s"Set nsd socket path to '${cfg.hardwareConfiguration.nsdConfiguration.socket}'")

      val systemDaemon = context.system.actorOf(SystemDaemon.props(
        cfg.hardwareConfiguration.nsdConfiguration.socket,
        cfg.hardwareConfiguration.nsdConfiguration.maxReq,
        FiniteDuration(cfg.hardwareConfiguration.nsdConfiguration.responseCleanInterval, TimeUnit.MILLISECONDS),
        cfg.hardwareConfiguration.emulation), "SystemDaemon"
      )

      //--- rf

      logger.info(s"Set wsd socket path to '${cfg.hardwareConfiguration.rfConfiguration.socket}'")

      val rfBridge = context.system.actorOf(RfBridge.props(
        cfg.hardwareConfiguration.rfConfiguration.socket,
        cfg.hardwareConfiguration.rfConfiguration.maxReq,
        FiniteDuration(cfg.hardwareConfiguration.rfConfiguration.responseCleanInterval, TimeUnit.MILLISECONDS),
        new RealNativeApiProxy(),
        eventer,
        cmdReplicator,
        new RealActorSystemProxy(context.system)), "RfBridge"
      )

      //--- bridges container for cmd replicator
      cmdReplicator ! CmdReplicator.Commands.SetBridges(CmdReplicator.Supply.BridgeRefs(serialBridge, rfBridge))

      //--- rf configurer

      val rfConfigurer = context.system.actorOf(RfConfigurer.props(cfg.hardwareConfiguration.rfConfiguration, rfBridge))
      rfConfigurer ! RfConfigurer.Commands.Exec()


      //--- virtualization

      logger.info(s"Set max vm instance count to '${cfg.virtualizationConfiguration.maxVm}'")
      logger.info(s"Set base vm instance count to '${cfg.virtualizationConfiguration.minVm}'")
      logger.info(s"Set script in memory cache time to '${cfg.virtualizationConfiguration.scriptCacheTime}'")
      logger.info(s"Set script in memory cache clean interval to '${cfg.virtualizationConfiguration.scriptCacheCleanInterval}'")
      val vmPool = context.system.actorOf(VmPool.props(cfg.virtualizationConfiguration.maxVm, cfg.virtualizationConfiguration.minVm), "VmPool")

      //--- storage

      logger.info(s"Set storage path to '${cfg.storageConfiguration.path}'")
      val storage = context.system.actorOf(StorageRep.props(cfg.storageConfiguration.path, new RealFilesProxy), "Storage")

      //--- shell

      logger.info(s"Set shell endpoint host to '${cfg.shellConfiguration.host}'")
      logger.info(s"Set shell endpoint port to '${cfg.shellConfiguration.port}'")
      logger.info(s"Set shell tls keystore path to '${cfg.shellConfiguration.keystoreFile}'")

      //--- HTTPS context

      implicit val serverMat = ActorMaterializer.create(context.system)

      val password: Array[Char] = cfg.shellConfiguration.keystorePass.toCharArray
      val ks: KeyStore = KeyStore.getInstance("PKCS12")
      val storeFile = new File(cfg.shellConfiguration.keystoreFile)

      if (Files.exists(storeFile.toPath) && Files.isReadable(storeFile.toPath)) {
        try {
          val keystore: InputStream = new FileInputStream(storeFile)
          ks.load(keystore, password)
          val keyManagerFactory: KeyManagerFactory = KeyManagerFactory.getInstance("SunX509")
          keyManagerFactory.init(ks, password)

          val tmf: TrustManagerFactory = TrustManagerFactory.getInstance("SunX509")
          tmf.init(ks)

          val sslContext: SSLContext = SSLContext.getInstance("TLS")
          sslContext.init(keyManagerFactory.getKeyManagers, tmf.getTrustManagers, new SecureRandom)
          val https: HttpsConnectionContext = ConnectionContext.https(sslContext)

          //--- build all

          val systemCommandsRep = context.system.actorOf(SyscomsRep.props(s"${data.workDir}/syscoms.conf"), "SyscomsRep")
          val userCommandsRep = context.system.actorOf(UsercomsRep.props(s"${data.workDir}/usercoms.conf"), "UsercomsRep")
          val accessRep = context.system.actorOf(AccessRep.props(s"${data.workDir}/access.conf", new RealFilesProxy), "AccessRep")
          val scriptsRep = context.system.actorOf(ScriptsRep.props(s"${data.workDir}/ucmd", cfg.virtualizationConfiguration.scriptCacheTime, cfg.virtualizationConfiguration.scriptCacheCleanInterval), "ScriptsRep")
          val bootRep = context.system.actorOf(BootRep.props(s"${data.workDir}/boot.conf"), "BootRep")

          val env = Env(
            syscomsRep = systemCommandsRep,
            usercomsRep = userCommandsRep,
            accessRep = accessRep,
            scriptsRep = scriptsRep,
            bootRep = bootRep,
            serialBridge = serialBridge,
            rfBridge = rfBridge,
            systemDaemon = systemDaemon,
            vmPool = vmPool,
            storageRep = storage,
            eventer = eventer,
            system = Some(context.system)
          )
          val runtime = context.system.actorOf(Runtime.props(env), "Runtime")
          val env2 = env.copy(runtime = runtime)
          val rest = context.system.actorOf(RestLayer.props(env2), "RestLayer")
          rest ! RestLayer.Commands.RunServer(cfg.shellConfiguration.host, cfg.shellConfiguration.port, https)

          //--- Boot manager
          val bootManager = context.actorOf(BootManager.props(env2, new RealActorSystemProxy(context.system)))
          bootManager ! BootManager.Commands.Exec()

          goto(WaitShellEndpointBinding)
        } catch {
          case e: Throwable =>
            logger.fatal(s"Unable to create http context because '${e.getMessage}'")
            context.system.terminate()
            stop
        }
      } else {
        logger.fatal("Unable to read shell tls keystore file")
        context.system.terminate()
        stop
      }

    case Event(StateTimeout, _) =>
      implicit val logQualifier = LogEntryQualifier("ConfigureLog_StateTimeout")
      logger.fatal("Main repository does not respond with expected timeout")
      context.system.terminate()
      stop
  }


  /** Waits for a response from node's http layer about the results of the binding. If the result is positive,
    * the initialization of the node is considered successful. If the result is negative, then this is a fatal error
    * and the system stops working. The timeout for the response from the repository is also a fatal error that stops
    * the program.
    */
  when(WaitShellEndpointBinding, 30 second) {
    case Event(ServerBinding(_), _) =>
      implicit val logQualifier = LogEntryQualifier("WaitShellEndpointBinding_BindingSuccess")
      logger.info(s"Node was successfully started")
      stop

    case Event(Status.Failure(reason), _) =>
      implicit val logQualifier = LogEntryQualifier("WaitShellEndpointBinding_BindingFailed")
      logger.fatal(s"Shell endpoint binding failed. Reason - $reason")
      context.system.terminate()
      stop

    case Event(StateTimeout, _) =>
      implicit val logQualifier = LogEntryQualifier("ConfigureShell_StateTimeout")
      logger.fatal("Http layer does not respond with expected timeout")
      context.system.terminate()
      stop
  }

  initialize()
}