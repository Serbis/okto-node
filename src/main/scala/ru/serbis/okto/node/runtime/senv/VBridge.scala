package ru.serbis.okto.node.runtime.senv

import akka.actor.ActorRef
import scala.concurrent.Await
import scala.concurrent.duration._
import akka.pattern.ask
import ru.serbis.okto.node.hardware.SerialBridge
import ru.serbis.okto.node.log.Logger.LogEntryQualifier
import ru.serbis.okto.node.log.StreamLogger

/** Serial bridge script api
  *
  * @param bridge serial bridge ref
  */
class VBridge(bridge: ActorRef) extends StreamLogger {

  setLogSourceName(s"VBridge*${System.currentTimeMillis()}")
  setLogKeys(Seq("VBridge"))

  implicit val logQualifier = LogEntryQualifier("static")

  val META = 543

  /** Send synchronous request to the serial bridge
    *
    * @param req request string
    * @return response string or null if some going wrong
    */
  def req(req: String): String = {
    try {
      Await.result(bridge.ask(SerialBridge.Commands.SerialRequest(req, META))(3 second), 3 second) match {
        case SerialBridge.Responses.SerialResponse(resp, META) =>
          logger.debug(s"Received response from serial bridge '$req'")
          resp
        case SerialBridge.Responses.ResponseTimeout(META) =>
          logger.warning(s"Hardware response timeout")
          null
        case SerialBridge.Responses.HardwareError(reason, META) =>
          logger.warning(s"Hardware error. Reason: '$reason'")
          null
        case SerialBridge.Responses.BridgeOverload(META) =>
          logger.warning(s"Serial bridge is overloaded")
          null
        case m =>
          logger.warning(s"Received unexpected response from the serial bridge '$m''")
          null
      }
    } catch {
      case _: Throwable =>
        logger.warning(s"Unable receive response from serial bridge - response timeout'")
        null
    }
  }
}
