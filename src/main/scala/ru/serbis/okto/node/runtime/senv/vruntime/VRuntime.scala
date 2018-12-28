package ru.serbis.okto.node.runtime.senv.vruntime

import java.util.concurrent.{LinkedBlockingQueue, Semaphore}
import java.util.concurrent.atomic.AtomicBoolean

import akka.actor.ActorRef
import akka.pattern.ask
import ru.serbis.okto.node.log.Logger.LogEntryQualifier
import ru.serbis.okto.node.log.StreamLogger
import ru.serbis.okto.node.reps.ScriptsRep
import ru.serbis.okto.node.runtime.app.AppCmdExecutor
import ru.serbis.okto.node.runtime.senv.{VShell, VShellLocal}

import scala.concurrent.Await
import scala.concurrent.duration._

/** The object that is responsible for runtime functions
  *
  * @param executor script executor
  */
class VRuntime(executor: ActorRef, scriptsRep: ActorRef, tm: Boolean = true) extends StreamLogger {
  setLogSourceName(s"VRuntime*${System.currentTimeMillis()}")
  setLogKeys(Seq("VRuntime"))

  implicit val logQualifier = LogEntryQualifier("static")

  /** Current preloaded libs namespaces, where key is the library name and the value is map of namespace->code pairs */
  var libs: Map[String, Map[String, String]] = Map.empty

  /** Signal queue. Any signal that was sent to the script executor will be offered to this queue. See sig() method for
    * details how this element works in the script code */
  val sigQueue = new LinkedBlockingQueue[Int]

  /** Flag witch prohibited interrupt operation on the script thread. Interrupt action occurs at each signal receive
    * operation, but if this flag is set, it will be skipped. This feature is used for controlled out from the sleep
    * mode. See sleep method for more details */
  var mayInterrupted = new AtomicBoolean(true)

  /** mayInterrupted flag semaphore. It mutex lock any action at mayInterrupted flag until it will be released */
  val mayInterruptedMutex = new Semaphore(1)

  /** Creates a new local shell
    *
    * @return VShell object or null if something went wrong */
  def createLocalShell(): VShell = {
    implicit val logQualifier = LogEntryQualifier("createLocalShell")

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

  /** Freeze execution for some time. It is critical blocking operation - any signal witch will be received in sleep, do
    * not out script from of sleep. This function may be used for realize critical sleep operation in the script code -
    * situation when sleep can never be interrupted.
    *
    * @param ms sleep time in milliseconds
    */
  def sleepc(ms: Long) = {
    mayInterruptedMutex.acquire()
    mayInterrupted.set(false)
    mayInterruptedMutex.release()
    Thread.sleep(ms)
    mayInterruptedMutex.acquire()
    mayInterrupted.set(true)
    mayInterruptedMutex.release()
  }

  /** Freeze execution for some time. It is non critical blocking operation - any signal witch will be received in sleep,
    * break sleeping and return control to the script code
    *
    * @param ms sleep time in milliseconds
    * @return 0 if operation was completed without interruption or number of milliseconds that could not be defended in sleep
    */
  def sleep(ms: Long): Long = {
    val start = System.currentTimeMillis()
    try {
      Thread.sleep(ms)
      0
    } catch {
      case _: Exception => ms - (System.currentTimeMillis() - start)
    }
  }

  /** Extract signal from the signals queue and return it. If signals queue is empty, return 0 */
  def sig(): Int = {
    if (!sigQueue.isEmpty)
      sigQueue.poll()
    else
      0
  }

  /** Check if runtime has not processed signal */
  def hasSig(): Boolean = !sigQueue.isEmpty

  /** It exports the namespace it has from the library. It requests the library code, selects all the namespaces from it
    * and returns the contents of the desired namespace. If there are any errors, returns a special text, which will lead
    * to an error in the evolution of the program and by this error you can uniquely determine what happened. For the
    * runtime life, the library code is requested once, after which all namespaces are cached. Astrix as namespace retrives
    * content of the all namespaces in the library
    *
    * @param lib library script name
    * @param ns namespace name or * for all namespaces
    */
  def `import`(lib: String, ns: String): String = {
    implicit val logQualifier = LogEntryQualifier("import")

    val namespaces = if (libs.contains(lib)) {
      Right(libs(lib))
    } else {
      val timeout = if (tm) 0.5 second else 3 second
      val r = try {
        Await.result(scriptsRep.ask(ScriptsRep.Commands.GetScript(lib + ".js"))(timeout), timeout) match {
          case ScriptsRep.Responses.Script(code) =>
            Right(code)
          case ScriptsRep.Responses.ScriptNotFound =>
            logger.warning(s"Unable to import library '$lib', script not found")
            Left(s"lib_${lib}_not_found")
          case m =>
            logger.warning(s"Unable to import library '$lib', repository respond with unexpected message $m")
            Left(s"lib_${lib}_internal_error")
        }
      } catch {
        case _: Throwable =>
          logger.warning(s"Unable to import library '$lib', repository response timeout")
          Left(s"lib_${lib}_rep_timeout")
      }
      if (r.isRight) {
        val libCode = r.right.get
        val exStarts = """_export\s.*[^\s]""".r.findAllMatchIn(libCode).toList
        val exEnds = """export_""".r.findAllMatchIn(libCode).toList
        val nsMap = exStarts.map(v => {
          val eStart = v.start
          val eEnd = v.end
          val nName = libCode.substring(eStart, eEnd).split(" ")(1)
          val c = exEnds.foldLeft(Int.MaxValue)((a, m) => if (m.start > eStart && m.start <= a) m.start else a )
          nName -> libCode.substring(eStart + (eEnd - eStart), c)
        }).toMap
        libs += (lib -> nsMap)
        Right(nsMap)
      } else {
        Left(r.left.get)
      }
    }

    if (namespaces.isRight) {
      val blk = namespaces.right.get
      if (ns == "*") {
        logger.debug(s"Imported all namespaces from library '$lib'")
        blk.foldLeft("")((a, v) => a + v._2 + ",").dropRight(1)
      } else {
        val code = blk.get(ns)
        if (code.isDefined) {
          logger.debug(s"Imported namespace '$ns' from library '$lib'")
          code.get
        } else {
          logger.warning(s"Unable to import namespace '$ns' from library '$lib', namespace not found")
          s"lib_${lib}_ns_${ns}_not_found"
        }
      }
    } else {
      namespaces.left.get
    }
  }
}