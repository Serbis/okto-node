package ru.serbis.okto.node.syscoms.pm

import akka.actor.{ActorRef, FSM, Props}
import akka.util.ByteString
import ru.serbis.okto.node.common.Env
import ru.serbis.okto.node.common.FsmDefaults.{Data, State}
import ru.serbis.okto.node.common.NodeUtils.getOptions
import ru.serbis.okto.node.common.ReachTypes.ReachByteString
import ru.serbis.okto.node.log.Logger.LogEntryQualifier
import ru.serbis.okto.node.log.StreamLogger
import ru.serbis.okto.node.reps.{ScriptsRep, UsercomsRep}
import ru.serbis.okto.node.runtime.{Stream, StreamControls}
import scala.concurrent.duration._


/** Process manager sub-module for install mode (--install option). In this mode actor expect option -n with
  * command name and then request user input for script code. After receive a code, it must modify the configuration and
  * write received code to the disk.
  */
object PmInstall {

  /** @param nextArgs mode arguments
    * @param env node env object
    * @param stdIn command standard input
    * @param stdOut command standard output
    * @param testMode test mode
    */
  def props(nextArgs: Vector[String], env: Env, stdIn: ActorRef, stdOut: ActorRef, testMode: Boolean = false) =
    Props(new PmInstall(nextArgs, env, stdIn, stdOut, testMode))

  object States {

    /** see description of the state code */
    case object Idle extends State
    /** see description of the state code */
    case object CollectingCode extends State
    /** see description of the state code */
    case object WaitConfigCreation extends State
    /** see description of the state code */
    case object WaitScriptCreation extends State
  }

  object StatesData {

    /** n/c */
    case object Uninitialized extends Data
    /** n/c */
    case class InCollectingCode(cmd: String, inBuf: ByteString) extends Data
    /** n/c */
    case class InWaitConfigCreation(cmd: String, code: String) extends Data
    /** n/c */
    case object InWaitScriptCreation extends Data
  }

  object Commands {

    /** Start fsm work */
    case object Exec
  }
}

class PmInstall(nextArgs: Vector[String], env: Env, stdInt: ActorRef, stdOut: ActorRef, testMode: Boolean) extends FSM[State, Data] with StreamLogger {
  import PmInstall.Commands._
  import PmInstall.States._
  import PmInstall.StatesData._

  setLogSourceName(s"PmInstall*${self.path.name}")
  setLogKeys(Seq("PmInstall"))

  implicit val logQualifier = LogEntryQualifier("static")

  /** Original sender (Head Pm actor) */
  var orig = ActorRef.noSender

  startWith(Idle, Uninitialized)

  /** Staring mode. In this mode actor parse input arguments and send prompt to the standard output */
  when(Idle, 5 second) {

    /** Start fsm message */
    case Event(Exec, _) =>
      println("---SUS0.0---")
      orig = sender()

      val options = getOptions(nextArgs)

      if (!options.contains("-n")) {
        println("---SUS0.1---")
        orig ! Pm.Internals.Complete(10, "Option '-n' is not presented")
        stop
      } else {
        val name = options("-n")
        if (name.exists(v => v == '.' || v == '/' || v == ' ')) {
          println("---SUS0.2---")
          orig ! Pm.Internals.Complete(11, "Script name contain some restricted symbols")
          stop
        } else {
          println("---SUS0.3---")
          stdOut ! Stream.Commands.WriteWrapped(ByteString().prompt)
          goto(CollectingCode) using InCollectingCode(options("-n"), ByteString.empty)
        }
      }

    //NOT TESTABLE
    case Event(StateTimeout, _) =>
      implicit val logQualifier = LogEntryQualifier("Idle_StateTimeout")
      logger.warning("FSM doest not run with expected timeout")
      stop
  }

  /** Expecting script code from user */
  when(CollectingCode, if (testMode) 0.5 second else 60 second) {

    /** Data block from stdIn. If it is contain eoi char, it transform received data to the utf8 string, and
      * sent request to the usercoms repository to add new script definition in the configuration. If data block
      * does not contain eoi, this data was buffered and new data message expected. */
    case Event(Stream.Responses.Data(bs), data: InCollectingCode) =>
      val eoiIndex = bs.indexOf(StreamControls.EOI)
      if (eoiIndex == -1) {
        println("---SUS1.0---")
        stay using data.copy(inBuf = data.inBuf ++ bs)
      } else {
        println("---SUS1.1---")
        //TODO [5] ну а если за EOI блок данных, скрипт будет с хренью
        val code = (data.inBuf ++ bs.slice(0, bs.size - 1)).utf8String
        if (code.isEmpty) {
          println("---SUS1.2---")
          orig ! Pm.Internals.Complete(12, "Presented code is empty string")
          stop
        } else {
          println("---SUS1.3---")
          env.usercomsRep ! UsercomsRep.Commands.Create(UsercomsRep.Responses.UserCommandDefinition(data.cmd, s"${data.cmd}.js"))
          goto(WaitConfigCreation) using InWaitConfigCreation(data.cmd, code)
        }
      }

    /** Script code does not presented with specified timeout */
    case Event(StateTimeout, _) =>
      orig ! Pm.Internals.Complete(13, "Code does not presented within 60 seconds")
      stop

  }

  /** Waiting usercoms repository response */
  when(WaitConfigCreation, if (testMode) 0.5 second else 5 second) {

    /** Successfully created configuration entry. Send request to the scripts repository for create new script file */
    case Event(UsercomsRep.Responses.Created, data: InWaitConfigCreation) =>
      env.scriptsRep ! ScriptsRep.Commands.Create(data.cmd, data.code)
      goto(WaitScriptCreation) using InWaitScriptCreation

    /** Configuration entry does not created because script with specified name already exist in the configuration */
    case Event(UsercomsRep.Responses.Exist, _) =>
      orig ! Pm.Internals.Complete(14, "Command already installed")
      stop

    /** Some io error was occurred at configuration writing procedure. Command terminates with failure */
    case Event(UsercomsRep.Responses.WriteError, _) =>
      orig ! Pm.Internals.Complete(15, "Configuration IO error")
      stop

    /** Repository does not respond with expected timeout. Command terminates with failure */
    case Event(StateTimeout, _) =>
      orig ! Pm.Internals.Complete(16, "Internal error type 0")
      stop
  }

  /** Waiting scripts repository response*/
  when(WaitScriptCreation, if (testMode) 0.5 second else 5 second) {

    /** Successfully created scrupt file. Command terminates with success */
    case Event(ScriptsRep.Responses.Created, _) =>
      println("---SUS2---")
      orig ! Pm.Internals.Complete(0, "Success")
      stop

      /**Some io error was occurred at script writing procedure. Command terminates with failure */
    case Event(ScriptsRep.Responses.WriteError, _) =>
      orig ! Pm.Internals.Complete(17, "Script IO error")
      stop

      /** Repository does not respond with expected timeout. Command terminates with failure */
    case Event(StateTimeout, _) =>
      orig ! Pm.Internals.Complete(18, "Internal error type 1")
      stop
  }

  initialize()
}