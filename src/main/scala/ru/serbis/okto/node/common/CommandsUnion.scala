package ru.serbis.okto.node.common

import akka.actor.ActorRef

object CommandsUnion {
  object Commands {
    case class Run(process: ActorRef, streams: Map[Int, ActorRef])
  }

  object Responses {

    /** The exit code of the command is sent to the runtime after the command completes. At the moment, the following
      * exit codes are possible:
      *
      *   0 - normal completion of the command
      *   1 - error in the arguments passed to the command
      *   2 - I / O error to / from stream (see log for details)
      *   3 - internal program error
      * @param code
      */
    case class ExecutorFinished(code: Int)
  }
}
