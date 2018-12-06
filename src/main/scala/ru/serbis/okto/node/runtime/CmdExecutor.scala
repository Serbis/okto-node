package ru.serbis.okto.node.runtime

import akka.actor.FSM
import ru.serbis.okto.node.common.FsmDefaults.{Data, State}
import ru.serbis.okto.node.log.Logger.LogEntryQualifier
import ru.serbis.okto.node.log.StreamLogger
import ru.serbis.okto.node.testut.{ActorSystemExpander, RealActorSystem}

/** Command executor superclass */
abstract class CmdExecutor(systemEx: ActorSystemExpander, testMode: Boolean) extends FSM[State, Data] with StreamLogger {
  implicit val logQualifier = LogEntryQualifier("static")

  systemEx match {
    case system: RealActorSystem => system.system = context.system
    case _ =>
  }
}
