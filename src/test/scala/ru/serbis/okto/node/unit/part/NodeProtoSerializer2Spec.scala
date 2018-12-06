package ru.serbis.okto.node.unit.part

import akka.actor.ActorSystem
import akka.testkit.{ImplicitSender, TestKit}
import akka.util.ByteString
import org.scalatest.{BeforeAndAfterAll, Matchers, WordSpecLike}
import ru.serbis.okto.node.common.NodeProtoSerializer2
import ru.serbis.okto.node.common.ReachTypes.ReachList
import ru.serbis.okto.node.log.{StdOutLogger, StreamLogger}
import ru.serbis.okto.node.proto.messages._
import ru.serbis.okto.node.common.ReachTypes.ReachByteString

import scala.annotation.tailrec
import scala.collection.mutable

class NodeProtoSerializer2Spec extends TestKit(ActorSystem("TestSystem")) with ImplicitSender
  with WordSpecLike with Matchers with BeforeAndAfterAll with StreamLogger {

  override protected def beforeAll(): Unit = {
    initializeGlobalLogger(system)
    logger.addDestination(system.actorOf(StdOutLogger.props))
  }

  "NodeProtoSerializer2" must {
    "Serialize/Deserialize message Data" in {
      val serializer = NodeProtoSerializer2()
      val orig = Data(ByteString("abc").toProto)
      val serializedMessage = serializer.toBinary(orig)
      val deserializedMessage = serializer.fromBinary(serializedMessage.get)
      deserializedMessage.get shouldEqual orig
    }

    "Return None for message with incorrect manifest" in {
      val serializer = NodeProtoSerializer2()
      val orig = Data(ByteString("abc").toProto)
      val serializedMessage = serializer.toBinary(orig)
      val modifiedSerializedMessage = serializedMessage.get.toArray.to[mutable.ListBuffer]
      modifiedSerializedMessage(3) = 132.toByte
      val deserializedMessage = serializer.fromBinary(ByteString(modifiedSerializedMessage.toArray))
      deserializedMessage shouldEqual None
    }

    "Return None for message with corrupted manifest" in {
      val serializer = NodeProtoSerializer2()
      val deserializedMessage = serializer.fromBinary(ByteString("abc"))
      deserializedMessage shouldEqual None
    }

    "Return None for incorrect proto representation" in {
      val serializer = NodeProtoSerializer2()
      val orig = Data(ByteString("abc").toProto)
      val serializedMessage = serializer.toBinary(orig)
      val modifiedSerializedMessage = serializedMessage.get.toArray.to[mutable.ListBuffer]
      modifiedSerializedMessage(5) = 132.toByte
      val deserializedMessage = serializer.fromBinary(ByteString(modifiedSerializedMessage.toArray))
      deserializedMessage shouldEqual None
    }
  }
}
