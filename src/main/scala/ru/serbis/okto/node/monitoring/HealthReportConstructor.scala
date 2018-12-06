package ru.serbis.okto.node.monitoring

import akka.actor.{ActorIdentity, ActorRef, FSM, Identify, Props}
import ru.serbis.okto.node.common.FsmDefaults.{Data, State}
import ru.serbis.okto.node.log.Logger.LogEntryQualifier
import ru.serbis.okto.node.log.StreamLogger

import scala.concurrent.duration._

//TODO [10] доки по системе мониторинга
object HealthReportConstructor {
  /** n/c */
  def props() = Props(new HealthReportConstructor())

  object States {
    /** see description of the state code */
    case object Idle extends State
    /** see description of the state code */
    case object WaitActorIdentifies extends State
  }

  object StatesData {
    /** n/c */
    case object Uninitialized extends Data
    /** n/c */
    case class InWaitActorIdentifies(part: String, collected: Set[String] = Set.empty) extends Data
  }

  object Commands {
    case class Exec()
  }

  object Responses {
    case class HealthReport(report: String)
  }

  object Internals {
    case object ActorIdentifiesFinished
  }
}

class HealthReportConstructor extends FSM[State, Data] with StreamLogger {
  import HealthReportConstructor.States._
  import HealthReportConstructor.StatesData._
  import HealthReportConstructor.Commands._
  import HealthReportConstructor.Responses._
  import HealthReportConstructor.Internals._

  setLogSourceName(s"ProcessConstructor*${self.path.name}")
  setLogKeys(Seq("ProcessConstructor"))

  implicit val logQualifier = LogEntryQualifier("static")

  var orig = ActorRef.noSender

  startWith(Idle, Uninitialized)

  logger.debug("Report constructor was started")

  when(Idle, 5 second) {
    case Event(_: Exec, _) =>
      implicit val logQualifier = LogEntryQualifier("Idle_Exec")
      orig = sender()

      val total = Runtime.getRuntime.totalMemory() / 1000000
      val max = Runtime.getRuntime.maxMemory() / 1000000
      val free = Runtime.getRuntime.freeMemory() / 1000000
      val heapUsage = s"${total - free}/$total/$max"
      log.debug(s"Heap usage data was obtained '$heapUsage'")

      context.system.actorSelection("/user/*") ! Identify(0)
      setTimer("a", ActorIdentifiesFinished, 5 second)
      goto(WaitActorIdentifies) using InWaitActorIdentifies(s"Heap usage (use/total/max): $heapUsage\n")

    case Event(StateTimeout, _) =>
      implicit val logQualifier = LogEntryQualifier("Idle_StateTimeout")
      logger.warning("FSM doest not start with expected timeout")
      stop
  }

  when(WaitActorIdentifies, 10 second) {
    case Event(ActorIdentity(_, ref), data: InWaitActorIdentifies) =>
      implicit val logQualifier = LogEntryQualifier("WaitActorIdentifies_ActorIdentity")
      if (ref.isDefined)
        stay using data.copy(collected = data.collected + ref.get.path.toString)
      else
        stay

    case Event(ActorIdentifiesFinished, data: InWaitActorIdentifies) =>
      implicit val logQualifier = LogEntryQualifier("WaitActorIdentifies_ActorIdentity")
      val actors = data.collected.foldLeft("\n")((a, v) => s"$a$v\n")
      logger.debug(s"Collected work actors list $actors")
      orig ! HealthReport(s"${data.part}Actors:\n $actors")
      stop

    case Event(StateTimeout, _) =>
      implicit val logQualifier = LogEntryQualifier("ActorIdentifiesFinished_StateTimeout")
      logger.warning("Strange timeout")
      stop
  }

  initialize()
}
