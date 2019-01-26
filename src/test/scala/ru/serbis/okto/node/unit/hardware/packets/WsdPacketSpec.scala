package ru.serbis.okto.node.unit.hardware.packets

import akka.util.ByteString
import org.scalatest.{Matchers, WordSpecLike}
import ru.serbis.okto.node.hardware.packets.ExbPacket.ExbCommandPacket
import ru.serbis.okto.node.hardware.packets.WsdPacket
import ru.serbis.okto.node.hardware.packets.WsdPacket._

class WsdPacketSpec  extends WordSpecLike with Matchers {
  "WsdTransmitPacket" must {
    "serialized and deserialized" in {
      val bin = WsdTransmitPacket(99, 299, ExbCommandPacket(399, "abc"))
      val packet = WsdTransmitPacket(bin)
      packet.tid shouldEqual 99
      packet.addr shouldEqual 299
      packet.exbp.asInstanceOf[ExbCommandPacket].tid shouldEqual 399
      packet.exbp.asInstanceOf[ExbCommandPacket].command shouldEqual "abc"
    }

    "WsdPacket should correct deserialize type" in {
      val bin = WsdTransmitPacket(99, 299, ExbCommandPacket(399, "abc"))
      val packet = WsdPacket(bin).asInstanceOf[WsdTransmitPacket]
      packet.tid shouldEqual 99
      packet.addr shouldEqual 299
      packet.exbp.asInstanceOf[ExbCommandPacket].tid shouldEqual 399
      packet.exbp.asInstanceOf[ExbCommandPacket].command shouldEqual "abc"
    }

    "WsdPacket should return WsdBroken packet, if packet have incorrect size" in {
      val arr = Array.fill(WsdPacket.Constants.HEADER_SIZE_FULL + 4 - 1)(0 toByte)
      arr(WsdPacket.Constants.PREAMBLE_SIZE + 4) = WsdPacket.Constants.TYPE_TRANSMIT
      WsdPacket(ByteString(arr)).asInstanceOf[WsdBrokenPacket]
    }
  }

  "WsdReceivePacket" must {
    "serialized and deserialized" in {
      val bin = WsdReceivePacket(99, 299, ExbCommandPacket(399, "abc"))
      val packet = WsdReceivePacket(bin)
      packet.tid shouldEqual 99
      packet.addr shouldEqual 299
      packet.exbp.asInstanceOf[ExbCommandPacket].tid shouldEqual 399
      packet.exbp.asInstanceOf[ExbCommandPacket].command shouldEqual "abc"
    }

    "WsdPacket should correct deserialize type" in {
      val bin = WsdReceivePacket(99, 299, ExbCommandPacket(399, "abc"))
      val packet = WsdPacket(bin).asInstanceOf[WsdReceivePacket]
      packet.tid shouldEqual 99
      packet.addr shouldEqual 299
      packet.exbp.asInstanceOf[ExbCommandPacket].tid shouldEqual 399
      packet.exbp.asInstanceOf[ExbCommandPacket].command shouldEqual "abc"
    }
    "WsdPacket should return WsdBroken packet, if packet have incorrect size" in {
      val arr = Array.fill(WsdPacket.Constants.HEADER_SIZE_FULL + 4 - 1)(0 toByte)
      arr(WsdPacket.Constants.PREAMBLE_SIZE + 4) = WsdPacket.Constants.TYPE_RECEIVE
      WsdPacket(ByteString(arr)).asInstanceOf[WsdBrokenPacket]
    }

  }

  "WsdErrorPacket" must {
    "serialized and deserialized" in {
      val bin = WsdErrorPacket(99, 299, "abc")
      val packet = WsdErrorPacket(bin)
      packet.tid shouldEqual 99
      packet.code shouldEqual 299
      packet.message shouldEqual "abc"
    }

    "WsdPacket should correct deserialize type" in {
      val bin = WsdErrorPacket(99, 299, "abc")
      val packet = WsdPacket(bin).asInstanceOf[WsdErrorPacket]
      packet.tid shouldEqual 99
      packet.code shouldEqual 299
      packet.message shouldEqual "abc"
    }

    "WsdPacket should return WsdBroken packet, if packet have incorrect size" in {
      val arr = Array.fill(WsdPacket.Constants.HEADER_SIZE_FULL + 4 - 1)(0 toByte)
      arr(WsdPacket.Constants.PREAMBLE_SIZE + 4) = WsdPacket.Constants.TYPE_ERROR
      WsdPacket(ByteString(arr)).asInstanceOf[WsdBrokenPacket]
    }
  }

  "WsdSetPipeMatrixPacket" must {
    "serialized and deserialized" in {
      val matrix = PipeMatrix(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
      val bin = WsdSetPipeMatrixPacket(99, matrix)
      val packet = WsdSetPipeMatrixPacket(bin)
      packet.tid shouldEqual 99
      packet.matrix shouldEqual matrix
    }

    "WsdPacket should correct deserialize type" in {
      val matrix = PipeMatrix(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
      val bin = WsdSetPipeMatrixPacket(99, matrix)
      val packet = WsdPacket(bin).asInstanceOf[WsdSetPipeMatrixPacket]
      packet.tid shouldEqual 99
      packet.matrix shouldEqual matrix
    }

    "WsdPacket should return WsdBroken packet, if packet have incorrect size" in {
      val arr = Array.fill(WsdPacket.Constants.HEADER_SIZE_FULL + 40 - 1)(0 toByte)
      arr(WsdPacket.Constants.PREAMBLE_SIZE + 4) = WsdPacket.Constants.WSD_TYPE_SET_PIPES_MATRIX
      WsdPacket(ByteString(arr)).asInstanceOf[WsdBrokenPacket]
    }
  }

  "WsdResultPacket" must {
    "serialized and deserialized" in {
      val body = ByteString("abc")
      val bin = WsdResultPacket(99, body)
      val packet = WsdResultPacket(bin)
      packet.tid shouldEqual 99
      packet.body shouldEqual body
    }

    "serialized and deserialized for empty body" in {
      val body = ByteString("")
      val bin = WsdResultPacket(99, body)
      val packet = WsdResultPacket(bin)
      packet.tid shouldEqual 99
      packet.body shouldEqual body
    }

    "WsdPacket should correct deserialize type" in {
      val body = ByteString("abc")
      val bin = WsdResultPacket(99, body)
      val packet = WsdPacket(bin).asInstanceOf[WsdResultPacket]
      packet.tid shouldEqual 99
      packet.body shouldEqual body
    }

    "WsdPacket should return WsdBroken packet, if packet have incorrect size" in {
      val arr = Array.fill(WsdPacket.Constants.HEADER_SIZE_FULL - 1)(0 toByte)
      arr(WsdPacket.Constants.PREAMBLE_SIZE + 4) = WsdPacket.Constants.WSD_TYPE_RESULT
      WsdPacket(ByteString(arr)).asInstanceOf[WsdBrokenPacket]
    }
  }
}
