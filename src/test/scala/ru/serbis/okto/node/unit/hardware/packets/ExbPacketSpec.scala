package ru.serbis.okto.node.unit.hardware.packets

import akka.util.ByteString
import org.scalatest.{Matchers, WordSpecLike}
import ru.serbis.okto.node.hardware.packets.ExbPacket
import ru.serbis.okto.node.hardware.packets.ExbPacket._

class ExbPacketSpec  extends WordSpecLike with Matchers {
  "ExbCommandPacket" must {
    "serialized and deserialized" in {
      val bin = ExbCommandPacket(99, "abc")
      val packet = ExbCommandPacket(bin)
      packet.tid shouldEqual 99
      packet.command shouldEqual "abc"
    }

    "ExbPacket should correct deserialize type" in {
      val bin = ExbCommandPacket(99, "abc")
      val packet = ExbPacket(bin).asInstanceOf[ExbCommandPacket]
      packet.tid shouldEqual 99
      packet.command shouldEqual "abc"
    }

    "ExbPacket should return ExbBroken packet, if packet have incorrect size" in {
      val arr = Array.fill(ExbPacket.Constants.HEADER_SIZE_FULL - 1)(0 toByte)
      arr(ExbPacket.Constants.PREAMBLE_SIZE + 4) = ExbPacket.Constants.TYPE_COMMAND
      ExbPacket(ByteString(arr)).asInstanceOf[ExbBrokenPacket]
    }
  }

  "ExbResponsePacket" must {
    "serialized and deserialized" in {
      val bin = ExbResponsePacket(99, "abc")
      val packet = ExbResponsePacket(bin)
      packet.tid shouldEqual 99
      packet.response shouldEqual "abc"
    }

    "ExbPacket should correct deserialize type" in {
      val bin = ExbResponsePacket(99, "abc")
      val packet = ExbPacket(bin).asInstanceOf[ExbResponsePacket]
      packet.tid shouldEqual 99
      packet.response shouldEqual "abc"
    }

    "ExbPacket should return ExbBroken packet, if packet have incorrect size" in {
      val arr = Array.fill(ExbPacket.Constants.HEADER_SIZE_FULL - 1)(0 toByte)
      arr(ExbPacket.Constants.PREAMBLE_SIZE + 4) = ExbPacket.Constants.TYPE_RESPONSE
      ExbPacket(ByteString(arr)).asInstanceOf[ExbBrokenPacket]
    }
  }

  "ExbErrorPacket" must {
    "serialized and deserialized" in {
      val bin = ExbErrorPacket(99, 299, "abc")
      val packet = ExbErrorPacket(bin)
      packet.tid shouldEqual 99
      packet.code shouldEqual 299
      packet.message shouldEqual "abc"
    }

    "ExbPacket should correct deserialize type" in {
      val bin = ExbErrorPacket(99, 299, "abc")
      val packet = ExbPacket(bin).asInstanceOf[ExbErrorPacket]
      packet.tid shouldEqual 99
      packet.code shouldEqual 299
      packet.message shouldEqual "abc"
    }

    "ExbPacket should return ExbBroken packet, if packet have incorrect size" in {
      val arr = Array.fill(ExbPacket.Constants.HEADER_SIZE_FULL + 4 - 1)(0 toByte)
      arr(ExbPacket.Constants.PREAMBLE_SIZE + 4) = ExbPacket.Constants.TYPE_ERROR
      ExbPacket(ByteString(arr)).asInstanceOf[ExbBrokenPacket]
    }
  }

  "ExbEventAckPacket" must {
    "serialized and deserialized" in {
      val bin = ExbEventAckPacket(99, 10)
      val packet = ExbEventAckPacket(bin)
      packet.tid shouldEqual 99
      packet.result shouldEqual 10
    }

    "ExbPacket should correct deserialize type" in {
      val bin = ExbEventAckPacket(99, 10)
      val packet = ExbPacket(bin).asInstanceOf[ExbEventAckPacket]
      packet.tid shouldEqual 99
      packet.result shouldEqual 10
    }

    "ExbPacket should return ExbBroken packet, if packet have incorrect size" in {
      val arr = Array.fill(ExbPacket.Constants.HEADER_SIZE_FULL + 1 - 1)(0 toByte)
      arr(ExbPacket.Constants.PREAMBLE_SIZE + 4) = ExbPacket.Constants.TYPE_EVENT_ACK
      ExbPacket(ByteString(arr)).asInstanceOf[ExbBrokenPacket]
    }
  }


  "ExbEventPacket" must {
    "serialized and deserialized as TYPE_EVENT" in {
      val bin = ExbEventPacket(99, 299, confirmed = false, ByteString("abc"))
      val packet = ExbEventPacket(bin)
      packet.eid shouldEqual 299
      packet.tid shouldEqual 99
      packet.confirmed shouldEqual false
      packet.payload shouldEqual ByteString("abc")
    }

    "serialized and deserialized as TYPE_EVENTC" in {
      val bin = ExbEventPacket(99, 299, confirmed = true, ByteString("abc"))
      val packet = ExbEventPacket(bin)
      packet.eid shouldEqual 299
      packet.tid shouldEqual 99
      packet.confirmed shouldEqual true
      packet.payload shouldEqual ByteString("abc")
    }

    "ExbPacket should correct deserialize type as TYPE_EVENT" in {
      val bin = ExbEventPacket(99, 299, confirmed = false, ByteString("abc"))
      val packet = ExbPacket(bin).asInstanceOf[ExbEventPacket]
      packet.eid shouldEqual 299
      packet.tid shouldEqual 99
      packet.confirmed shouldEqual false
      packet.payload shouldEqual ByteString("abc")
    }

    "ExbPacket should correct deserialize type as TYPE_EVENTC" in {
      val bin = ExbEventPacket(99, 299, confirmed = true, ByteString("abc"))
      val packet = ExbPacket(bin).asInstanceOf[ExbEventPacket]
      packet.eid shouldEqual 299
      packet.tid shouldEqual 99
      packet.confirmed shouldEqual true
      packet.payload shouldEqual ByteString("abc")
    }

    "ExbPacket should return ExbBroken packet, if packet have incorrect size" in {
      val arr = Array.fill(ExbPacket.Constants.HEADER_SIZE_FULL + 4 - 1)(0 toByte)
      arr(ExbPacket.Constants.PREAMBLE_SIZE + 4) = ExbPacket.Constants.TYPE_EVENTC
      ExbPacket(ByteString(arr)).asInstanceOf[ExbBrokenPacket]

      arr(ExbPacket.Constants.PREAMBLE_SIZE + 4) = ExbPacket.Constants.TYPE_EVENT
      ExbPacket(ByteString(arr)).asInstanceOf[ExbBrokenPacket]
    }
  }
}
