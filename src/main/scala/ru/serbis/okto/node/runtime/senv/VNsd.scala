package ru.serbis.okto.node.runtime.senv

import akka.actor.ActorRef
import akka.pattern.ask
import ru.serbis.okto.node.hardware.{SerialBridge, SystemDaemon}
import ru.serbis.okto.node.log.Logger.LogEntryQualifier
import ru.serbis.okto.node.log.StreamLogger

import scala.concurrent.Await
import scala.concurrent.duration._

/** Node system daemon script api
  *
  * @param systemDaemon SystemDaemon ref
  */
class VNsd(systemDaemon: ActorRef) extends StreamLogger {

  setLogSourceName(s"VNsd*${System.currentTimeMillis()}")
  setLogKeys(Seq("VNsd"))

  implicit val logQualifier = LogEntryQualifier("static")

  val META = 134

  /** Send synchronous request to the system daemon
    *
    * @param req request string
    * @return response string or null if some going wrong
    */
  def req(req: String): String = {
    try {
      Await.result(systemDaemon.ask(SystemDaemon.Commands.DaemonRequest(req, META))(3 second), 3 second) match {
        case SystemDaemon.Responses.DaemonResponse(resp, META) =>
          logger.debug(s"Received response from NSD '$req'")
          resp
        case SystemDaemon.Responses.ResponseTimeout(META) =>
          logger.warning(s"NSD response timeout")
          null
        case SystemDaemon.Responses.DaemonError(reason, META) =>
          logger.warning(s"NSD error. Reason: '$reason'")
          null
        case SystemDaemon.Responses.DaemonOverload(META) =>
          logger.warning(s"NSD is overloaded")
          null
        case m =>
          logger.warning(s"Received unexpected response from the NSD '$m''")
          null
      }
    } catch {
      case _: Throwable =>
        logger.warning(s"Unable receive response from NSD - response timeout'")
        null
    }
  }
}
