package ru.serbis.okto.node.hardware

import java.math.BigInteger
import akka.actor.{ActorRef, FSM, Props}
import ru.serbis.okto.node.common.FsmDefaults.{Data, State}
import ru.serbis.okto.node.log.Logger.LogEntryQualifier
import ru.serbis.okto.node.log.StreamLogger
import ru.serbis.okto.node.reps.MainRep.Responses.RfConfiguration
import scala.concurrent.duration._

/** This actor makes the initial configuration of the wsd driver, configuring the nrf24 chip in accordance with the
  * parameters specified in the configuration. An error during the configuration of this subsystem is not a fatal
  * but extremely serious error, indicating either problems with the node configuration or problems of a hardware nature.
  */
object  RfConfigurer {

  /** @param config rf hardware configuration from main rep
    * @param rfBridge rf bridge ref
    * @param testMode test mode flag
    */
  def props(config: RfConfiguration, rfBridge: ActorRef, testMode: Boolean = false): Props =
    Props(new RfConfigurer(config, rfBridge, testMode))

  object States {
    /** see description of the state code */
    case object Idle extends State

    /** see description of the state code */
    case object WaitPipeConfiguration extends State
  }

  object StatesData {
    /** n/c */
    case object Uninitialized extends Data

    /** n/c */
    case object InWaitPipeConfiguration extends Data
  }

  object Commands {

    /** n/c */
    case class Exec()
  }

  object Responses {

    /** WSD was successful configured. This message sent to the sender only in the test mode */
    case object SuccessOperation

    /** WSD does not fully configured. This message sent to the sender only in the test mode */
    case object FailedOperation
  }
}

class RfConfigurer(config: RfConfiguration, rfBridge: ActorRef, testMode: Boolean = false) extends FSM[State, Data] with StreamLogger {
  import RfConfigurer.Commands._
  import RfConfigurer.States._
  import RfConfigurer.StatesData._
  import RfConfigurer.Responses._

  setLogSourceName(s"RfConfigurer*${self.path.name}")
  setLogKeys(Seq("RfConfigurer"))

  implicit val logQualifier = LogEntryQualifier("Static")

  var orig = ActorRef.noSender

  startWith(Idle, Uninitialized)

  /** Send SetPipeMatrix request to the RfBridge and go to waiting response. May terminate fsm, if config has incorrect
    * address definitions */
  when(Idle, 5 second) {
    case Event(req: Exec, _) =>
      implicit val logQualifier = LogEntryQualifier("Idle_Exec")
      orig = sender()

      try {
        val matrix = RfBridge.Commands.PipeMatrix(
          new BigInteger(config.p1_targ, 16).intValue(),
          new BigInteger(config.p1_self, 16).intValue(),
          new BigInteger(config.p2_targ, 16).intValue(),
          new BigInteger(config.p2_self, 16).intValue(),
          new BigInteger(config.p3_targ, 16).intValue(),
          new BigInteger(config.p3_self, 16).intValue(),
          new BigInteger(config.p4_targ, 16).intValue(),
          new BigInteger(config.p4_self, 16).intValue(),
          new BigInteger(config.p5_targ, 16).intValue(),
          new BigInteger(config.p5_self, 16).intValue()
        )

        rfBridge ! RfBridge.Commands.SetPipeMatrix(matrix)

        goto(WaitPipeConfiguration) using InWaitPipeConfiguration
      } catch {
        case e: Exception =>
          logger.error("Unable to configure WSD, some pipe address has wrong format")
          if (testMode)
            orig ! FailedOperation
          stop
      }

    //NOT TESTABLE
    case Event(StateTimeout, _) =>
      implicit val logQualifier = LogEntryQualifier("Idle_StateTimeout")
      logger.warning("Exec command wait timeout")
      stop
  }

  /** Wait response for SetPipeMatrix request. After recieve result, log it and stop the actor */
  when(WaitPipeConfiguration, if (testMode) 0.5 second else 10 second) {
    case Event(RfBridge.Responses.SuccessDriverOperation, _) =>
      implicit val logQualifier = LogEntryQualifier("WaitPipeConfiguration_InWaitPipeConfiguration")

      logger.info(s"WSD pipe matrix was successfully configured to [ [${config.p1_targ}, ${config.p1_self}], [${config.p2_targ}, ${config.p2_self}], [${config.p3_targ},${config.p3_self}], [${config.p4_targ}, ${config.p4_self}], [${config.p5_targ}, ${config.p5_self}] ]")
      if (testMode)
        orig ! SuccessOperation

      logger.info("WSD configuring finished")
      stop

    case Event(RfBridge.Responses.BadPipeMatrix, _) =>
      implicit val logQualifier = LogEntryQualifier("WaitPipeConfiguration_InWaitPipeConfiguration")
      logger.error("Unable to configure WSD, driver respond with bad pipe matrix error")
      if (testMode)
        orig ! FailedOperation
      stop

    case Event(RfBridge.Responses.BadPipeMsb, _) =>
      implicit val logQualifier = LogEntryQualifier("WaitPipeConfiguration_InWaitPipeConfiguration")
      logger.error("Unable to configure WSD, some pipe address has identical MSB")
      if (testMode)
        orig ! FailedOperation
      stop

    case Event(RfBridge.Responses.TransactionTimeout, _) =>
      implicit val logQualifier = LogEntryQualifier("WaitPipeConfiguration_InWaitPipeConfiguration")
      logger.error("Unable to configure WSD, driver does not respond")
      if (testMode)
        orig ! FailedOperation
      stop

    case Event(RfBridge.Responses.ChipNotRespond, _) =>
      implicit val logQualifier = LogEntryQualifier("WaitPipeConfiguration_InWaitPipeConfiguration")
      logger.error("Unable to configure WSD, chip does not respond for driver commands")
      if (testMode)
        orig ! FailedOperation
      stop

    case Event(StateTimeout, _) =>
      implicit val logQualifier = LogEntryQualifier("WaitPipeConfiguration_StateTimeout")
      logger.error("Unable to configure WSD, rf bridge does not respond")
      if (testMode)
        orig ! FailedOperation
      stop
  }

  initialize()
}
