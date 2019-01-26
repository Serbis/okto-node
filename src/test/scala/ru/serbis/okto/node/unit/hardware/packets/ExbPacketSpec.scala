package ru.serbis.okto.node.unit.hardware.packets

import akka.util.ByteString
import org.scalatest.{Matchers, WordSpecLike}
import ru.serbis.okto.node.hardware.packets.ExbPacket
import ru.serbis.okto.node.hardware.packets.ExbPacket.{ExbBrokenPacket, ExbCommandPacket, ExbErrorPacket, ExbResponsePacket}

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
}
