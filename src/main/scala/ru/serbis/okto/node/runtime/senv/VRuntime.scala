package ru.serbis.okto.node.runtime.senv

import akka.actor.ActorRef
import ru.serbis.okto.node.log.Logger.LogEntryQualifier
import ru.serbis.okto.node.log.StreamLogger
import ru.serbis.okto.node.runtime.AppCmdExecutor
import scala.concurrent.duration._
import akka.pattern.ask

import scala.concurrent.Await

/** The object that is responsible for runtime functions
  *
  * @param executor script executor
  */
class VRuntime(executor: ActorRef) extends StreamLogger {
  setLogSourceName(s"VRuntime*${System.currentTimeMillis()}")
  setLogKeys(Seq("VRuntime"))

  implicit val logQualifier = LogEntryQualifier("static")

  /** Creates a new local shell
    *
    * @return VShell object or null if something went wrong */
  def createLocalShell(): VShell = {
    try {
      Await.result(executor.ask(AppCmdExecutor.Commands.CreateLocalShell())(3 second), 3 second) match {
        case defi: AppCmdExecutor.Responses.ShellDefinition =>
          logger.debug("New local shell was created")
          new VShellLocal(defi.process, defi.stdIn, defi.stdOut)
        case m =>
          logger.warning(s"Unable to create new local shell because : 'Unexpected message from executor '$m''")
          null
      }
    } catch {
      case e: Throwable =>
        logger.warning(s"Unable to create new local shell because : '${e.getMessage}'")
        null
    }
  }

  /** Freeze execution for some time
    *
    * @param ms sleep time in milliseconds
    */
  def sleep(ms: Long) = {
    Thread.sleep(ms)
  }
}