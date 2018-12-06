package ru.serbis.okto.node.adapter

import akka.NotUsed
import akka.actor.{Actor, Props}
import akka.http.scaladsl.{ConnectionContext, Http}
import akka.http.scaladsl.model.ws.{BinaryMessage, Message}
import akka.http.scaladsl.server.Directives._
import akka.http.scaladsl.server.Route
import akka.pattern.{ask, pipe}
import akka.stream.scaladsl.{Flow, Sink, Source}
import akka.stream.{ActorMaterializer, OverflowStrategy}
import ru.serbis.okto.node.common.Env
import ru.serbis.okto.node.log.Logger.LogEntryQualifier
import ru.serbis.okto.node.log.StreamLogger
import ru.serbis.okto.node.monitoring.HealthReportConstructor

import scala.concurrent.duration._

/** Implementing the http layer of a node */
object RestLayer {

  /** @param env node's env object */
  def props(env: Env) = Props(new RestLayer(env))

  object Commands {

    /** Starts the http server's binding procedure. The answer to this message is the result of executing the future
      * that contains the binding object or error.
      *
      * @param host ip or hostname
      * @param port port
      */
    case class RunServer(host: String, port: Int, context: ConnectionContext)
  }
}

class RestLayer(env: Env) extends Actor with StreamLogger {
  import RestLayer.Commands._

  implicit val system = context.system
  implicit val materializer = ActorMaterializer.create(context.system)
  implicit val executionContext = context.system.dispatcher

  setLogSourceName(s"Shell*${self.path.name}")
  setLogKeys(Seq("Shell"))

  implicit val logQualifier = LogEntryQualifier("static")

  logger.info("RestLayer actor is initialized")

  val route =
    /** The end point of the node's shell. This address is the conversion point from the http protocol to the websocket.
      * To connect to it, you should use the http mechanism of changing the protocol Connection Upgrade.*/
    path("shell") {
      get {
        extractUpgradeToWebSocket { _ =>
          handleWebSocketMessages(newTunnel())
        }
      }
    } ~
    path("health") {
      get {
        val hpc = system.actorOf(HealthReportConstructor.props(), s"HealthReportConstructor_${System.currentTimeMillis()}")
        complete(hpc.ask(HealthReportConstructor.Commands.Exec())(10 second) map (v => v.asInstanceOf[HealthReportConstructor.Responses.HealthReport].report))
      }
    }

  override def receive = {
    /** See the message description */
    case RunServer(host, port, httpCtx) =>
      implicit val logQualifier = LogEntryQualifier("RunServer")

      logger.info(s"Start binding procedure to $host:$port")
      Http().bindAndHandle(route, host, port, connectionContext = httpCtx) pipeTo sender
  }

  /** Creates a WebSocket handler for the /shell endpoint */
  def newTunnel(): Flow[Message, Message, NotUsed] = {
    val tunnelActor = context.system.actorOf(ReactiveShellTunnel.props(env), s"ReactiveShellTunnel_${System.currentTimeMillis()}")

    val incomingMessages: Sink[Message, NotUsed] =
      Flow[Message].map {
        case BinaryMessage.Strict(data) => ReactiveShellTunnel.Commands.Receive(data)
      }.to(Sink.actorRef[ReactiveShellTunnel.Commands.Receive](tunnelActor, ReactiveShellTunnel.Commands.Close))

    val outgoingMessages: Source[Message, NotUsed] =
      Source.actorRef[BinaryMessage](10, OverflowStrategy.fail)
        .mapMaterializedValue { outActor =>
          tunnelActor ! ReactiveShellTunnel.Commands.Connected(outActor)
          NotUsed
        }.map((outMsg: BinaryMessage) => outMsg)

    Flow.fromSinkAndSource(incomingMessages, outgoingMessages)
  }
}
