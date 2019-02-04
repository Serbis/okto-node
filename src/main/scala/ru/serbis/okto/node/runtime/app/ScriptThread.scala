package ru.serbis.okto.node.runtime.app

import akka.actor.ActorRef
import akka.util.ByteString
import javax.script.{ScriptContext, ScriptEngine, ScriptException}
import org.parboiled2.ParseError
import ru.serbis.okto.node.log.Logger.LogEntryQualifier
import ru.serbis.okto.node.log.StreamLogger
import ru.serbis.okto.node.runtime.Stream.Commands.WriteWrapped
import ru.serbis.okto.node.runtime.StreamControls.{EOF, EOI, PROMPT}
import ru.serbis.okto.node.runtime.senv.ScriptEnvInstance
import ru.serbis.okto.node.syscoms.shell.Shell.States.StatementsProcessingMode
import ru.serbis.okto.node.syscoms.shell.Shell.StatesData.InStatementsProcessingMode
import ru.serbis.okto.node.syscoms.shell.StatementsParser

import scala.util.matching.Regex
import scala.util.{Failure, Success}

/** The thread of executing a user script. It initializes the global variables of the runtime and starts the code
  * evaluation, which is a blocking operation.
  *
  * @param scriptEnv script environment
  * @param engine script vm
  * @param script script code
  * @param args script args
  */
class ScriptThread(scriptEnv: ScriptEnvInstance, engine: ScriptEngine, script: String, args: Vector[String]) extends Runnable with StreamLogger {

  setLogSourceName(s"ScriptThread**")
  setLogKeys(Seq("ScriptThread"))

  override def run() = {
    implicit val logQualifier = LogEntryQualifier("run")
    val context = engine.getContext

    //Load global objects
    context.setAttribute("stdOut", scriptEnv.stdOut, ScriptContext.ENGINE_SCOPE)
    context.setAttribute("stdIn", scriptEnv.stdIn, ScriptContext.ENGINE_SCOPE)
    context.setAttribute("runtime", scriptEnv.runtime, ScriptContext.ENGINE_SCOPE)
    context.setAttribute("program", scriptEnv.scriptControl, ScriptContext.ENGINE_SCOPE)
    context.setAttribute("bridge", scriptEnv.bridge, ScriptContext.ENGINE_SCOPE)
    context.setAttribute("nsd", scriptEnv.nsd, ScriptContext.ENGINE_SCOPE)
    context.setAttribute("storage", scriptEnv.storage, ScriptContext.ENGINE_SCOPE)
    context.setAttribute("http", scriptEnv.http, ScriptContext.ENGINE_SCOPE)
    context.setAttribute("exit", () => 0, ScriptContext.ENGINE_SCOPE)
    context.setAttribute("quit", () => 0, ScriptContext.ENGINE_SCOPE)

    //TODO [5] нужно во все экзекуторы ввести pid, иначе все логи оказываются безымянными
    //Combine system script code with user code
    val finalScript = preproc()

    try {
      logger.debug("Script runtime evaluate code")
      engine.eval(finalScript)
    } catch {
      case e: ScriptException =>
        logger.warning(s"Script runtime was stopped deu script error: ${e.getMessage}")
        scriptEnv.stdOut.write(s"Execution error: ${e.getMessage}")
        scriptEnv.scriptControl.exit(3)
      case o: Exception => //NOT TESTABLE
        logger.error(s"Script runtime was stopped deu unknown error: ${o.getMessage}")
        scriptEnv.stdOut.write(s"Unknown script execution error")
        scriptEnv.scriptControl.exit(3)
    }
  }

  /** Prepares the code for running on a virtual machine. The essence of preparation is reduced to the preprocessing of the
    * code in terms of import substitutions and creating entry and exit points from the program. */
  def preproc(): String = {
    val iMatches = """import[^;]*;""".r.findAllMatchIn(script).toList
    val x = iMatches.foldLeft("")((a, v) => {
      val l = script.substring(v.start, v.end)
      a + l
    })
    val imports = ImportParser(x).Flow.run() match {
      case Success(r) => Some(r)
      case Failure(_: ParseError) => None
    }

    val imEvals = if (imports.isDefined) {
      imports.get.foldLeft("")((a, v) => {
        a + v.targets.foldLeft("")((s, b) => {
          s + s"""eval('var ${b.as} = {' + runtime.import('${v.from}', '${b.ns}') + '}');""" + "\n"
        })
      })
    } else ""
    val fScript = iMatches.foldLeft(script)((a, v) => {
      val l = script.substring(v.start, v.end)
      a.replace(l, "")
    })
    s"$imEvals$fScript\nmain([${args.foldLeft("")((a, v) => a + s"'$v', ").dropRight(2)}]);\nprogram.exit(0);"
  }
}