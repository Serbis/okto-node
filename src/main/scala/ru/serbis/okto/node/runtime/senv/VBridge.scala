package ru.serbis.okto.node.runtime.senv

import akka.actor.ActorRef

import scala.concurrent.Await
import scala.concurrent.duration._
import akka.pattern.ask
import ru.serbis.okto.node.hardware.{RfBridge, SerialBridge}
import ru.serbis.okto.node.log.Logger.LogEntryQualifier
import ru.serbis.okto.node.log.StreamLogger

/** Serial bridge script api
  *
  * @param serialBridge serial bridge ref
  * @param rfBridge rf bridge ref
  */
class VBridge(serialBridge: ActorRef, rfBridge: ActorRef) extends StreamLogger {

  setLogSourceName(s"VBridge*${System.currentTimeMillis()}")
  setLogKeys(Seq("VBridge"))

  implicit val logQualifier = LogEntryQualifier("static")

  /** Send synchronous request to the exb. If address set to 0, request to wired exb will be send. If address > 0, it will
    * be determined as request to rf exb.
    *
    * @param addr exb address
    * @param req request string
    * @return transaction result, see it description for details
    */
  def req(addr: Int, req: String): BridgeReqResult = {
    try {
      if (addr == 0) {
        logger.debug(s"Send request to the SerialBridge [ req='$req' ] ")
        Await.result(serialBridge.ask(SerialBridge.Commands.ExbCommand(req, 3000))(5 second), 5 second) match {
          case SerialBridge.Responses.ExbResponse(resp, _) =>
            logger.debug(s"Received exb response from wired exb [ req='$req', resp='$resp' ]")
            new BridgeReqResult(0, resp)
          case SerialBridge.Responses.ExbError(code, message, _) =>
            logger.debug(s"Received exb error from wired exb [ req='$req', code='$code', message='$message' ]")
            new BridgeReqResult(1, s"$code/$message")
          case SerialBridge.Responses.BridgeOverload =>
            logger.warning(s"SerialBridge is overloaded [ addr=$addr, req='$req' ]")
            new BridgeReqResult(4, "")
          case SerialBridge.Responses.TransactionTimeout =>
            logger.info(s"Wired exb does not respond [ req='$req' ]")
            new BridgeReqResult(6, "")
          case m =>
            logger.warning(s"Received unexpected response from the SerialBridge '$m' [ req='$req' ]'")
            new BridgeReqResult(8, "")
        }
      } else {
        logger.debug(s"Send request to the RfBridge [ addr=$addr, req='$req' ] ")
        Await.result(rfBridge.ask(RfBridge.Commands.ExbCommand(addr, req, 3000))(5 second), 5 second) match {
          case RfBridge.Responses.ExbResponse(resp, _) =>
            logger.debug(s"Received exb response [ addr=$addr, req='$req', resp='$resp' ]")
            new BridgeReqResult(0, resp)
          case RfBridge.Responses.ExbError(code, message, _) =>
            logger.debug(s"Received exb error [ addr=$addr, req='$req', code='$code', message='$message' ]")
            new BridgeReqResult(1, s"$code/$message")
          case RfBridge.Responses.ExbAddrNotDefined =>
            logger.warning(s"Exb address if not defined in the driver [ addr=$addr, req='$req' ]")
            new BridgeReqResult(2, "")
          case RfBridge.Responses.ExbUnreachable =>
            logger.info(s"Exb is unreachable [ addr=$addr, req='$req' ]")
            new BridgeReqResult(3, "")
          case RfBridge.Responses.BridgeOverload =>
            logger.warning(s"RfBridge is overloaded [ addr=$addr, req='$req' ]")
            new BridgeReqResult(4, "")
          case RfBridge.Responses.ExbBrokenResponse =>
            logger.warning(s"RfBridge receive broken response from exb [ addr=$addr, req='$req' ]")
            new BridgeReqResult(5, "")
          case RfBridge.Responses.TransactionTimeout =>
            logger.info(s"Exb does not respond [ addr=$addr, req='$req' ]")
            new BridgeReqResult(6, "")
          case RfBridge.Responses.DriverError =>
            logger.error(s"RfBridge receive driver error [ addr=$addr, req='$req' ]")
            new BridgeReqResult(7, "")
          case m =>
            logger.warning(s"Received unexpected response from the RfBridge '$m' [ addr=$addr, req='$req' ]'")
            new BridgeReqResult(8, "")
        }
      }
    } catch {
      case _: Throwable =>
        logger.warning(s"Unable receive response from rf/serial bridge - response timeout'")
        new BridgeReqResult(9, "")
    }
  }

  /** Response object for req method. Determine result of some exb transaction
    *
    * @param error error code, 0 if operation was success. Each code determine some specific err situations, and may bt:
    *              1 - Exb respond with error
    *              2 - Exb address does not defined in the driver
    *              3 - Exb is unreachable
    *              4 - Bridge overload
    *              5 - Broken exb response
    *              6 - Exb does not respond with transaction timeout
    *              7 - Driver error
    *              8 - Unexpected response from the bridg
    * @param result text payload
    */
  class BridgeReqResult(val error: Int, val result: String)
}
