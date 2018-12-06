package ru.serbis.okto.node.common

import java.nio.ByteBuffer

import akka.util.ByteString
import ru.serbis.okto.node.log.Logger.LogEntryQualifier
import ru.serbis.okto.node.log.StreamLogger
import ru.serbis.okto.node.proto.messages._
import ReachTypes.ReachByteString
import com.google.protobuf.InvalidProtocolBufferException

import scala.util.Try

/** Internal universal message serializer based on the Protobuf stack. A serialized message consists of a manifest
  * that is an integer with a byte order of BE, followed by a block of bytes of the serialized message.
  */
object NodeProtoSerializer2 {
  def apply() = new NodeProtoSerializer2
}

class NodeProtoSerializer2 extends StreamLogger {

  setLogSourceName(s"NodeProtoSerializer2_NA")
  setLogKeys(Seq("NodeProtoSerializer2"))

  implicit val logQualifier = LogEntryQualifier("static")

  /** Messages manifests */
  final val m_Data = 1002

  /** Deserialize message from binary representation
    *
    * @param bs serialized message
    * @return message or None, if the message was not by deserialized
    */
  def fromBinary(bs: ByteString): Option[AnyRef] = {
    implicit val logQualifier = LogEntryQualifier("fromBinary")

    if (bs.size < 4) {
      logger.error(s"Could not deserialize input data [ ${bs.toHexString} ]. Manifest is corrupted")
      None
    } else {
      val manifest = bs.slice(0, 4).toInt
      Try {
        manifest match {
          case `m_Data` => Some(Data.parseFrom(bs.drop(4).toArray))
          case _ =>
            logger.error(s"Could not serialize the $manifest object. The serialization method is not defined")
            None

        }
      } recover {
        case _: InvalidProtocolBufferException =>
          logger.error(s"Could not serialize the $manifest object. Proto block is corrupted")
          None
        case e =>
          logger.error(s"Could not serialize the $manifest object. Unknown error $e")
          None
      } get
    }
  }

  /** Serialize message to binary representation
    *
    * @param o object for serialization
    * @return ByteString or None, if the message was not by serialized
    */
  def toBinary(o: AnyRef): Option[ByteString] =  {
    implicit val logQualifier = LogEntryQualifier("toBinary")

    val bf = ByteBuffer.allocate(4)
    o match {
      case v: Data => Some(ByteString(bf.putInt(m_Data).array()) ++ ByteString(v.toByteArray))
      case _ =>
        logger.error(s"Could not serialize the $o object. The serialization method is not defined")
        None
    }
  }
}
