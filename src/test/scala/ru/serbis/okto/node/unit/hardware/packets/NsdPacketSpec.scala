package ru.serbis.okto.node.unit.hardware.packets

import akka.util.ByteString
import org.scalatest.{Matchers, WordSpecLike}
import ru.serbis.okto.node.hardware.packets.NsdPacket
import ru.serbis.okto.node.hardware.packets.NsdPacket._

class NsdPacketSpec  extends WordSpecLike with Matchers {
  "NsdCmdPacket" must {
    "serialized and deserialized" in {
      val bin = NsdCmdPacket(99,  ByteString("abc"))
      val packet = NsdCmdPacket(bin)
      packet.tid shouldEqual 99
      packet.cmd shouldEqual ByteString("abc")
    }

    "NsdPacket should correct deserialize type" in {
      val bin = NsdCmdPacket(99,  ByteString("abc"))
      val packet = NsdPacket(bin).asInstanceOf[NsdCmdPacket]
      packet.tid shouldEqual 99
      packet.cmd shouldEqual ByteString("abc")
    }

    "NsdPacket should return NsdPacket packet, if packet have incorrect size" in {
      val arr = Array.fill(NsdPacket.Constants.HEADER_SIZE_FULL - 1)(0 toByte)
      arr(NsdPacket.Constants.PREAMBLE_SIZE + 4) = NsdPacket.Constants.NSD_TYPE_CMD
      NsdPacket(ByteString(arr)).asInstanceOf[NsdBrokenPacket]
    }
  }

  "NsdErrorPacket" must {
    "serialized and deserialized" in {
      val bin = NsdErrorPacket(99, 299, "abc")
      val packet = NsdErrorPacket(bin)
      packet.tid shouldEqual 99
      packet.code shouldEqual 299
      packet.message shouldEqual "abc"
    }

    "NsdPacket should correct deserialize type" in {
      val bin = NsdErrorPacket(99, 299, "abc")
      val packet = NsdPacket(bin).asInstanceOf[NsdErrorPacket]
      packet.tid shouldEqual 99
      packet.code shouldEqual 299
      packet.message shouldEqual "abc"
    }

    "NsdPacket should return NsdPacket packet, if packet have incorrect size" in {
      val arr = Array.fill(NsdPacket.Constants.HEADER_SIZE_FULL + 4 - 1)(0 toByte)
      arr(NsdPacket.Constants.PREAMBLE_SIZE + 4) = NsdPacket.Constants.NSD_TYPE_ERROR
      NsdPacket(ByteString(arr)).asInstanceOf[NsdBrokenPacket]
    }
  }

  "NsdResultPacket" must {
    "serialized and deserialized" in {
      val body = ByteString("abc")
      val bin = NsdResultPacket(99, body)
      val packet = NsdResultPacket(bin)
      packet.tid shouldEqual 99
      packet.body shouldEqual body
    }

    "serialized and deserialized for empty body" in {
      val body = ByteString("")
      val bin = NsdResultPacket(99, body)
      val packet = NsdResultPacket(bin)
      packet.tid shouldEqual 99
      packet.body shouldEqual body
    }

    "NsdPacket should correct deserialize type" in {
      val body = ByteString("abc")
      val bin = NsdResultPacket(99, body)
      val packet = NsdPacket(bin).asInstanceOf[NsdResultPacket]
      packet.tid shouldEqual 99
      packet.body shouldEqual body
    }

    "NsdPacket should return NsdPacket packet, if packet have incorrect size" in {
      val arr = Array.fill(NsdPacket.Constants.HEADER_SIZE_FULL - 1)(0 toByte)
      arr(NsdPacket.Constants.PREAMBLE_SIZE + 4) = NsdPacket.Constants.NSD_TYPE_RESULT
      NsdPacket(ByteString(arr)).asInstanceOf[NsdBrokenPacket]
    }
  }
}
