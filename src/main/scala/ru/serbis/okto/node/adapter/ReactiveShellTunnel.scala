package ru.serbis.okto.node.adapter

import akka.actor.SupervisorStrategy.Stop
import akka.actor.{Actor, ActorRef, PoisonPill, Props}
import akka.http.scaladsl.model.ws.{BinaryMessage, TextMessage}
import akka.util.ByteString
import ru.serbis.okto.node.common.{Env, NodeProtoSerializer2}
import ru.serbis.okto.node.log.Logger.LogEntryQualifier
import ru.serbis.okto.node.log.StreamLogger
import ru.serbis.okto.node.runtime.{ProcessConstructor, Runtime, Stream}
import ru.serbis.okto.node.common.ReachTypes.ReachByteString
import ru.serbis.okto.node.runtime.Stream.Commands.{Write, WriteWrapped}
import ru.serbis.okto.node.runtime.Stream.Responses.Data
import ru.serbis.okto.node.proto.{messages => proto_messages}
import ru.serbis.okto.node.common.ReachTypes.ReachProtoByteString
import ru.serbis.okto.node.reps.SyscomsRep.Responses.SystemCommandDefinition
import ru.serbis.okto.node.reps.UsercomsRep.Responses.UserCommandDefinition
import ru.serbis.okto.node.runtime.StreamControls._
import ru.serbis.okto.node.runtime.Process
import ru.serbis.okto.node.syscoms.shell.Shell

import scala.concurrent.duration._
import scala.collection.mutable
import scala.concurrent.Await

/** This actor acts as a WebSocket handler on the client side. It is created by the rest layer of the node in response
  * to the http request to the service / shell. Its task is to start the shell program immediately after the connection
  * is established, serializing / deserializing data going from shell to connection and vice versa.
  */
object ReactiveShellTunnel {

  /** @param env node's env object */
  def props(env: Env) = Props(new ReactiveShellTunnel(env))

  object Commands {

    /** The handler for getting data from the connection. Currently, only messages like proto.Data deserialize and
      * direct their contents to the standard input of the shell program.
      *
      * @param data binary data from connection
      **/
    case class Receive(data: ByteString)

    /** Determines the fact of establishing a connection with a low-level WebSocket gateway. Having received this
      * message, the shell program is created, with which the work of this tunnel will continue in the future.
      *
      * @param ref reference through which you can send data to a connection
      */
    case class Connected(ref: ActorRef)

    /** Closes the tunnel. If the tunnel created or creates a shell, EOF will be sent to its standard input. The main
      * sender of this message is the connection actor, which generates it when the channel is closed by the client
      */
    case object Close
  }
}

class ReactiveShellTunnel(env: Env) extends Actor with StreamLogger {
  import ReactiveShellTunnel.Commands._

  setLogSourceName(s"ReactiveShellTunnel*${self.path.name}")
  setLogKeys(Seq("ReactiveShellTunnel"))

  implicit val logQualifier = LogEntryQualifier("static")

  /** Connection reference */
  var connection: Option[ActorRef] = None

  /** Node proto serializer object */
  var serializer = NodeProtoSerializer2()

  /** StdIn of the active shell program */
  var shellStdIn: Option[ActorRef] = None

  /** StdOut of the active shell program */
  var shellStdOut: Option[ActorRef] = None

  /** Shell executor actor */
  var shellExecutor: Option[ActorRef] = None

  /** Data buffer on the connection side. Used when the data came from the connection, but the shell program has not
    * yet been fully initialized */
  var inBuffer = ByteString.empty

  var inSpawn = false

  var mustStop = false

  logger.debug("Created new reactive shell tunnel")

  override def receive = {

    /** See the message description */
    case Connected(ref) =>
      implicit val logQualifier = LogEntryQualifier("Connected")
      connection = Some(ref)
      logger.debug("New connection source reference was established. Run shell wrapper...")
      env.runtime ! Runtime.Commands.Spawn("shell", Vector("tunnel"), SystemCommandDefinition(""), self, "tunnel")
      inSpawn = true

    /** See the message description */
    case Receive(data) =>
      implicit val logQualifier = LogEntryQualifier("Receive")
      val deserializedMessage = serializer.fromBinary(data)
      if (deserializedMessage.isDefined) {
        deserializedMessage.get match  {
          case t: proto_messages.Data =>
            logger.debug(s"Received data message from the connection with payload '${t.data.toAkka.toHexString}'")
            if (shellStdIn.isDefined) {
              shellStdIn.get.tell(WriteWrapped(t.data.toAkka), ActorRef.noSender)
            } else {
              inBuffer = inBuffer ++ t.data.toAkka
            }

          case proto_messages.Action(t) if t == 0 =>
            logger.debug(s"Received action type 'close'. Initiated shell die")
            if (inSpawn) {
              mustStop = true
            } else {
              shellExecutor.get ! Shell.Commands.Die
              logger.debug("Tunnel was stopped")
              connection.get ! PoisonPill
              context.stop(self)
            }

          case proto_messages.Action(t) if t == 1 =>
            logger.debug(s"Received action type 'keepalive'. Shell life was prolongate")
            if (!inSpawn) {
              shellExecutor.get ! Shell.Commands.KeepAlive
            }

          case _ => //NOT TESTABLE while program have only one message
            logger.warning(s"Unexpected message from ws tunnel '${deserializedMessage.get}'")
        }

      } else {
        logger.warning(s"Massage deserialization from connection was failed. Bad data is '${data.toHexString}'")
      }

    /** It fixes the fact of creating the shell process. Flush the internal data buffer in the standard input of the
      * process of the shell.
      */
    case m: ProcessConstructor.Responses.ProcessDef =>
      if (!mustStop) {
        m.ref ! Process.Commands.Start
        shellStdOut = Some(m.streams(0))
        shellStdIn = Some(m.streams(1))
        shellExecutor = Some(m.executor)
        shellStdOut.get ! Stream.Commands.Attach(self)
        shellStdOut.get ! Stream.Commands.Flush

        logger.info(s"New shell process was successfully created with pid '${m.pid}'")

        if (inBuffer.nonEmpty)
          shellStdIn.get.tell(WriteWrapped(inBuffer), ActorRef.noSender)

        inSpawn = false
      } else {
        m.streams(1).tell(Stream.Commands.Write(ByteString(Array(EOF))), ActorRef.noSender)
        logger.debug("Tunnel was stopped")
        connection.get ! PoisonPill
        self ! Stop
      }


    /** Handles the receipt of data from the shell. Serializes the received message and sends it to the connection */
    case Data(data) =>
      implicit val logQualifier = LogEntryQualifier("Data")
      val serializedMessage = serializer.toBinary(ru.serbis.okto.node.proto.messages.Data(data.toProto))
      if (serializedMessage.isDefined) {
        logger.debug(s"Write serialized message the connection '${data.toProto}'")
        connection.get ! BinaryMessage(serializedMessage.get)
      } else { //NOT TESTABLE
        logger.warning(s"Message serialization from internal area was failed. Bad message is '$connection'. ??? was sent to the sender")
      }

    /** See the message description */
    case Close =>
      if (inSpawn) {
        mustStop = true
      } else {
        shellStdIn.get.tell(Stream.Commands.Write(ByteString(Array(EOF))), ActorRef.noSender)
        logger.debug("Tunnel was stopped be peer")
        connection.get ! PoisonPill
        context.stop(self)
      }
  }
}

//TODO [5] см комментарий ниже
// Собственно к вопросу о закрытии шелла если соединение со стороны клиенты было оборвано. Доупустип это решено в случае
// с нахождением в командном режиме. Но помимо командного режиме, есть еще режмы в парсинга инициализации и работы.
// Перехват EOF в них не реализован, поэтому обязательно нужно продумать как его сделать, иначе обрыв соединения
// например в режиме Interaction приведет к появлению мервого экземпляра шелла управляемого только таймауетами. В
// данном просе идет обыгрывание ситуации из разрада - а что если данные со стороны клиента придут например в момент
// парсинга.
