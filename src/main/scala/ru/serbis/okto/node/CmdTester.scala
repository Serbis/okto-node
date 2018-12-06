package ru.serbis.okto.node

import akka.actor.{Actor, ActorRef, ActorSystem, Props}
import akka.http.scaladsl.Http
import akka.http.scaladsl.model.StatusCodes
import akka.http.scaladsl.model.ws.{BinaryMessage, Message, WebSocketRequest}
import akka.stream.scaladsl.{Flow, Keep, Sink, Source}
import akka.stream.{ActorMaterializer, OverflowStrategy}
import akka.{Done, NotUsed}
import ru.serbis.okto.node.common.NodeProtoSerializer2
import ru.serbis.okto.node.log.{StdOutLogger, StreamLogger}

import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future

class TestActor extends Actor {

  val serializer = NodeProtoSerializer2()



  override def receive = {
    case ref: ActorRef =>
      //ref ! BinaryMessage(serializer.toBinary(ExecCmd("echo hello")).get)
  }
}

object CmdTester extends /*App with*/ StreamLogger {
  implicit val system = ActorSystem("ast")
  implicit val materializer = ActorMaterializer()
  val testActor = system.actorOf(Props(new TestActor))

  initializeGlobalLogger(system)
  logger.addDestination(system.actorOf(StdOutLogger.props))

  val serializer = NodeProtoSerializer2()

  // print each incoming strict text message
  val printSink: Sink[Message, Future[Done]] =
    Sink.foreach {
      case message: BinaryMessage.Strict =>
        println(serializer.fromBinary(message.data).get)
    }

  val helloSource: Source[Message, NotUsed] = Source.actorRef[BinaryMessage](10, OverflowStrategy.fail)
    .mapMaterializedValue { outActor =>
      testActor ! outActor
      NotUsed
    }.map((outMsg: BinaryMessage) => outMsg)

  //val helloSource = Source.single(BinaryMessage(serializer.toBinary(ExecCmd("echo hello")).get))

    //Source.single(BinaryMessage(serializer.toBinary(ExecCmd("echo hello")).get))

  // the Future[Done] is the materialized value of Sink.foreach
  // and it is completed when the stream completes
  val flow: Flow[Message, Message, Future[Done]] =
  Flow.fromSinkAndSourceMat(printSink, helloSource)(Keep.left)

  // upgradeResponse is a Future[WebSocketUpgradeResponse] that
  // completes or fails when the connection succeeds or fails
  // and closed is a Future[Done] representing the stream completion from above
  val (upgradeResponse, closed) =
  Http().singleWebSocketRequest(WebSocketRequest("ws://10.200.0.12:5000/shell"), flow)

  val connected = upgradeResponse.map { upgrade =>
    // just like a regular http request we can access response status which is available via upgrade.response.status
    // status code 101 (Switching Protocols) indicates that server support WebSockets
    if (upgrade.response.status == StatusCodes.SwitchingProtocols) {
      Done
    } else {
      throw new RuntimeException(s"Connection failed: ${upgrade.response.status}")
    }
  }

  // in a real application you would not side effect here
  // and handle errors more carefully
  connected.onComplete(println)
  closed.foreach(_ => println("closed"))
}