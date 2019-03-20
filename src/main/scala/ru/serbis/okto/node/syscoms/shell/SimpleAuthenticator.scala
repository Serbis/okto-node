package ru.serbis.okto.node.syscoms.shell

import akka.actor.{ActorRef, FSM, Props}
import ru.serbis.okto.node.access.{AccessCredentials, AccessRep}
import ru.serbis.okto.node.common.Env
import ru.serbis.okto.node.common.FsmDefaults.{Data, State}
import ru.serbis.okto.node.log.Logger.LogEntryQualifier
import ru.serbis.okto.node.log.StreamLogger

import scala.concurrent.duration._

/** Performs simple username / password authentication */
object SimpleAuthenticator {

  /** @param env global env object
    * @param testMode test mode flag
    */
  def props(env: Env, testMode: Boolean = false) =
    Props(new SimpleAuthenticator(env, testMode))

  object States {

    /** see description of the state code */
    case object Idle extends State

    /** see description of the state code */
    case object WaitResponse extends State
  }

  object StatesData {

    /** n/c */
    case object Uninitialized extends Data

    /** @param user user name
      * @param password plain user password
      */
    case class InWaitResponse(user: String, password: String) extends Data
  }

  object Commands {

    /** @param args vector with user, password pait
      */
    case class Exec(args: Vector[String])
  }
}

class SimpleAuthenticator(env: Env, tm: Boolean) extends FSM[State, Data] with StreamLogger  {
  import SimpleAuthenticator.Commands._
  import SimpleAuthenticator.States._
  import SimpleAuthenticator.StatesData._

  var orig: ActorRef = ActorRef.noSender

  setLogSourceName(s"SimpleAuthenticator*${self.path.name}")
  setLogKeys(Seq("SimpleAuthenticator"))


  startWith(Idle, Uninitialized)

  when(Idle, 5 second) {

    /** Request AccessRep for AccessCredentials for specified user. If username / pair does not full, respond with
      * InsufficientInputData to the originator */
    case Event(Exec(args), _) =>
      implicit val logQualifier = LogEntryQualifier("Idle_Exec")

      orig = sender()

      if (args.size >= 2) {
        val user = args.head
        val password = args(1)
        logger.debug(s"User credentials request from AccessRep [ user=$user, password=*** ]")
        env.accessRep ! AccessRep.Commands.GetPermissionsDefinition(user)

        goto(WaitResponse) using InWaitResponse(user, password)
      } else {
        logger.debug(s"Unable to request user credentials request from AccessRep, insufficient data [ user=${args.headOption}, password=? ]")
        orig ! Shell.Internals.InsufficientInputData
        stop
      }

    //NOT TESTABLE
    case Event(StateTimeout, _) =>
      implicit val logQualifier = LogEntryQualifier("Idle_StateTimeout")
      logger.warning("FSM doest not run with expected timeout")
      stop
  }

  /** Receive response from the AccessRep */
  when(WaitResponse, if (tm) 0.5 second else 5 second) {

    /** User credentials was found in the system. Plain password hashed and compared with password from the user
      * credentials. If it is equals respond with AuthSuccess to thr originator, else with AuthFailed */
    case Event(cred: AccessCredentials.UserCredentials, InWaitResponse(user, pwdPlain)) =>
      implicit val logQualifier = LogEntryQualifier("WaitResponse_UserCredentials")
      val pwdHash = AccessCredentials.hashPassword(pwdPlain, cred.salt)
      if (pwdHash == cred.password) {
        logger.debug(s"Authentication failed, password equals [ user=$user, password=*** ]")
        orig ! Shell.Internals.AuthSuccess(cred)
        stop
      } else {
        logger.debug(s"Authentication failed, password doe not equals [ user=$user, password=*** ]")
        orig ! Shell.Internals.AuthFailed("Incorrect password")
        stop
      }

    /** User credentials does not found in the system. Respond with AuthFailed to the originator */
    case Event(AccessRep.Responses.NotExist, InWaitResponse(user, _)) =>
      implicit val logQualifier = LogEntryQualifier("WaitResponse_NotExist")
      logger.debug(s"Authentication failed, user does not exist [ user=$user, password=*** ]")
      orig ! Shell.Internals.AuthFailed("User does not exist")
      stop

    /** Repository does not respond. Respond with AuthTimeout to the originator */
    case Event(StateTimeout, InWaitResponse(user, _)) =>
      implicit val logQualifier = LogEntryQualifier("WaitResponse_StateTimeout")
      logger.debug(s"Authentication failed, repository doe not respond [ user=$user, password=*** ]")
      orig ! Shell.Internals.AuthTimeout
      stop
  }

  initialize()
}

