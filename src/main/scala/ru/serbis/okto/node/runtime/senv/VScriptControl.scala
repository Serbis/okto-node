package ru.serbis.okto.node.runtime.senv

import akka.actor.ActorRef
import ru.serbis.okto.node.runtime.app.AppCmdExecutor

/** Object-container functions to manage the operation of the script
  *
  * @param executor executor actor reference
  */
class VScriptControl(executor: ActorRef) {
  var exited = false

  /** Exits the script with a specific code.
    *
    * Attention: this method always implicitly called at the end of the script
    *
    * @param code exit code
    */
  def exit(code: Int) = {
    if (!exited) {
      executor.tell(AppCmdExecutor.Commands.Exit(code), ActorRef.noSender)
      exited = true
    }
  }
}