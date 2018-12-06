package ru.serbis.okto.node.syscoms.pm

import akka.actor.{ActorRef, FSM, Props}
import ru.serbis.okto.node.common.Env
import ru.serbis.okto.node.common.FsmDefaults.{Data, State}
import ru.serbis.okto.node.log.Logger.LogEntryQualifier
import ru.serbis.okto.node.log.StreamLogger
import ru.serbis.okto.node.reps.UsercomsRep

import scala.concurrent.duration._

/** Process manager sub-module for list mode (--list option). In this mode actor must request full command list from
  * the configuration and return it in nl separated form as the command result.
  */
object PmList {

  /** @param nextArgs mode arguments
    * @param env node env object
    * @param stdIn command standard input
    * @param stdOut command standard output
    * @param testMode test mode
    */
  def props(nextArgs: Vector[String], env: Env, stdIn: ActorRef, stdOut: ActorRef, testMode: Boolean = false) =
    Props(new PmList(nextArgs, env, stdIn, stdOut, testMode))

  object States {
    /** see description of the state code */
    case object Idle extends State
    /** see description of the state code */
    case object WaitConfigBatch extends State
  }

  object StatesData {
    /** n/c */
    case object Uninitialized extends Data
    /** n/c */
    case object InWaitConfigBatch extends Data
  }

  object Commands {

    /** Start fsm work */
    case object Exec
  }
}

class PmList(nextArgs: Vector[String], env: Env, stdInt: ActorRef, stdOut: ActorRef, testMode: Boolean) extends FSM[State, Data] with StreamLogger {
  import PmList.Commands._
  import PmList.States._
  import PmList.StatesData._

  setLogSourceName(s"PmList*${self.path.name}")
  setLogKeys(Seq("PmList"))

  implicit val logQualifier = LogEntryQualifier("static")

  /** Original sender (Head Pm actor) */
  var orig = ActorRef.noSender

  startWith(Idle, Uninitialized)

  /** Staring mode. In this mode actor send request to get full commands list from the usercoms repository */
  when(Idle, 5 second) {
    case Event(Exec, _) =>
      orig = sender()
      env.usercomsRep ! UsercomsRep.Commands.GetCommandsBatch(List.empty)
      goto(WaitConfigBatch) using InWaitConfigBatch

    //NOT TESTABLE
    case Event(StateTimeout, _) =>
      implicit val logQualifier = LogEntryQualifier("Idle_StateTimeout")
      logger.warning("FSM doest not run with expected timeout")
      stop
  }

  /** Receiving response from usercoms repository */
  when(WaitConfigBatch,  if (testMode) 0.5 second else 5 second) {

    /** List with commands definitions. This list transforms to the nl separated string and return as result of the
      * command */
    case Event(UsercomsRep.Responses.CommandsBatch(batch), _) =>
      orig ! Pm.Internals.Complete(0, batch.foldLeft("")((a, v) => a + s"${v._1}\n").dropRight(1))
      stop

    /** Repository does not respond with expected timeout. Command terminates with failure */
    case Event(StateTimeout, _) =>
      orig ! Pm.Internals.Complete(30, "Internal error type 0")
      stop
  }

  initialize()
}