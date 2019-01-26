package ru.serbis.okto.node.hardware.packets

import java.nio.ByteBuffer
import akka.util.ByteString
import ru.serbis.okto.node.hardware.packets.ExbPacket.BasicExbPacket

/** This functionality is used to work with WSD packages. The file contains objects for each type of package. Each such
  * object has two applicative functions. The first takes a ByteString as input and returns a package definition. The
  * second takes packet specific parameters and populates a ByteString. Similarly, in the head object WsdPacket, an
  * applicative function is defined that accepts a ByteString and returns a specific packet derived from the type field.
  *
  * In all applicative functions that work with binary data, if the binary representation of the package is incorrect,
  * it will return the type WrongWsdPacket.
  *
  * Example:
  *   val bin = WsdTransmitPacket(99, 299, ExbCommandPacket(399, "abc"))
  *   val packet = WsdTransmitPacket(bin)
  *
  *   val packet2 = WsdPacket(bin)
  *
  */
object WsdPacket {
  object Constants {
    val PREAMBLE: Long =  0x3FAAAAAAAAAAAAAAL
    val PREAMBLE_SIZE =  8
    val HEADER_SIZE_FULL = PREAMBLE_SIZE + 7

    val TYPE_TRANSMIT: Byte = 0
    val TYPE_RECEIVE: Byte = 1
    val TYPE_ERROR: Byte = 2
    val WSD_TYPE_SET_PIPES_MATRIX: Byte =  3
    val WSD_TYPE_RESULT: Byte = 4

    val ERROR_ADDR_UNREACHABLE = 1000
    val ERROR_ADDR_NOT_DEFINED = 1001
    val ERROR_BROKEN_PIPE_MATRIX = 1002
    val ERROR_BAD_PIPE_MSB = 1003
    val ERROR_CHIP_NOT_RESPOND = 1004
  }

  def apply(bin: ByteString): BasicWsdPacket = {
    bin(Constants.PREAMBLE_SIZE + 4) match {
      case Constants.TYPE_TRANSMIT =>
        if (bin.size < Constants.HEADER_SIZE_FULL + 4) WsdBrokenPacket()
        else WsdTransmitPacket(bin)
      case Constants.TYPE_RECEIVE =>
        if (bin.size < Constants.HEADER_SIZE_FULL + 4) WsdBrokenPacket()
        else WsdReceivePacket(bin)
      case Constants.TYPE_ERROR =>
        if (bin.size < Constants.HEADER_SIZE_FULL + 4) WsdBrokenPacket()
        else WsdErrorPacket(bin)
      case Constants.WSD_TYPE_SET_PIPES_MATRIX =>
        if (bin.size < Constants.HEADER_SIZE_FULL + 40) WsdBrokenPacket()
        else WsdSetPipeMatrixPacket(bin)
      case Constants.WSD_TYPE_RESULT =>
        if (bin.size < Constants.HEADER_SIZE_FULL) WsdBrokenPacket()
        else WsdResultPacket(bin)
      case _ => WsdBrokenPacket()
    }
  }

  def marshall(tid: Int, `type`: Byte, data: ByteString): ByteString = {
    val bin: Array[Byte] = Array.fill(Constants.HEADER_SIZE_FULL + data.size)(0)

    var buffer = ByteBuffer.allocate(8) //PREAMBLE
    buffer.putLong(Constants.PREAMBLE)
    bin(0) = buffer.get(0)
    bin(1) = buffer.get(1)
    bin(2) = buffer.get(2)
    bin(3) = buffer.get(3)
    bin(4) = buffer.get(4)
    bin(5) = buffer.get(5)
    bin(6) = buffer.get(6)
    bin(7) = buffer.get(7)

    buffer = ByteBuffer.allocate(4) //TID
    buffer.putInt(tid)
    bin(8) = buffer.get(0)
    bin(9) = buffer.get(1)
    bin(10) = buffer.get(2)
    bin(11) = buffer.get(3)

    bin(12) = `type` //TYPE

    buffer = ByteBuffer.allocate(2) //LENGTH
    buffer.putShort(data.size.toShort)
    bin(13) = buffer.get(0)
    bin(14) = buffer.get(1)

    if (data.nonEmpty)
      System.arraycopy(data.toArray, 0, bin, Constants.HEADER_SIZE_FULL, data.size)

    ByteString(bin)
  }

  def unmarshall(bin: ByteString): WsdRawPacket = {

    val arr = Array( //TID
      bin(8),
      bin(9),
      bin(10),
      bin(11)
    )
    val wrapped = ByteBuffer.wrap(arr)
    val tid = wrapped.getInt()

    val `type` = bin(12) //TYPE

    new WsdRawPacket(tid, `type`,bin.slice(15, bin.size))
  }



  //-----------------------------------------------------------------------------------------------

  object WsdErrorPacket {
    def apply(tid: Int, code: Int, message: String): ByteString = toBinary(tid, code, message)
    def apply(bin: ByteString): WsdErrorPacket = fromBinary(bin)

    private def fromBinary(bin: ByteString) = {
      val raw = unmarshall(bin)

      val arr = Array(
        raw.body(0),
        raw.body(1),
        raw.body(2),
        raw.body(3)
      )
      val wrapped = ByteBuffer.wrap(arr)
      val code = wrapped.getInt()

      new WsdErrorPacket(raw.tid, code, raw.body.slice(4, raw.body.size).utf8String)
    }

    private def toBinary(tid: Int, code: Int, message: String) =  {
      val bin: Array[Byte] = Array.fill(4)(0)

      val buffer = ByteBuffer.allocate(4)
      buffer.putInt(code)
      bin(0) = buffer.get(0)
      bin(1) = buffer.get(1)
      bin(2) = buffer.get(2)
      bin(3) = buffer.get(3)

      marshall(tid, Constants.TYPE_ERROR, ByteString(bin) ++ ByteString(message))
    }
  }

  class WsdErrorPacket(override val tid: Int, val code: Int, val message: String) extends BasicWsdPacket

  //-----------------------------------------------------------------------------------------------

  object WsdResultPacket {
    def apply(tid: Int,  body: ByteString): ByteString = toBinary(tid, body)
    def apply(bin: ByteString): WsdResultPacket = fromBinary(bin)

    private def fromBinary(bin: ByteString) = {
      val raw = unmarshall(bin)

      new WsdResultPacket(raw.tid, raw.body)
    }

    private def toBinary(tid: Int, body: ByteString) =  {
      marshall(tid, Constants.WSD_TYPE_RESULT, body)
    }
  }

  class WsdResultPacket(override val tid: Int, val body: ByteString) extends BasicWsdPacket

  //-----------------------------------------------------------------------------------------------

  object WsdReceivePacket {
    def apply(tid: Int, addr: Int, exbp: ByteString): ByteString = toBinary(tid, addr, exbp)
    def apply(bin: ByteString): WsdReceivePacket = fromBinary(bin)

    private def fromBinary(bin: ByteString) = {
      val raw = unmarshall(bin)

      val arr = Array(
        raw.body(0),
        raw.body(1),
        raw.body(2),
        raw.body(3)
      )
      val wrapped = ByteBuffer.wrap(arr)
      val addr = wrapped.getInt()

      new WsdReceivePacket(raw.tid, addr, ExbPacket(raw.body.slice(4, raw.body.size)))
    }

    private def toBinary(tid: Int, addr: Int, exbp: ByteString) =  {
      val bin: Array[Byte] = Array.fill(4)(0)

      val buffer = ByteBuffer.allocate(4)
      buffer.putInt(addr)
      bin(0) = buffer.get(0)
      bin(1) = buffer.get(1)
      bin(2) = buffer.get(2)
      bin(3) = buffer.get(3)

      marshall(tid, Constants.TYPE_RECEIVE, ByteString(bin) ++ exbp)
    }
  }

  class WsdReceivePacket(override val tid: Int, val addr: Int, val exbp: BasicExbPacket) extends BasicWsdPacket

  //-----------------------------------------------------------------------------------------------

  object WsdTransmitPacket {
    def apply(tid: Int, addr: Int, exbp: ByteString): ByteString = toBinary(tid, addr, exbp)
    def apply(bin: ByteString): WsdTransmitPacket = fromBinary(bin)

    private def fromBinary(bin: ByteString) = {
      val raw = unmarshall(bin)

      val arr = Array(
        raw.body(0),
        raw.body(1),
        raw.body(2),
        raw.body(3)
      )
      val wrapped = ByteBuffer.wrap(arr)
      val addr = wrapped.getInt()

      new WsdTransmitPacket(raw.tid, addr, ExbPacket(raw.body.slice(4, raw.body.size)))
    }

    private def toBinary(tid: Int, addr: Int, exbp: ByteString) =  {
      val bin: Array[Byte] = Array.fill(4)(0)

      val buffer = ByteBuffer.allocate(4)
      buffer.putInt(addr)
      bin(0) = buffer.get(0)
      bin(1) = buffer.get(1)
      bin(2) = buffer.get(2)
      bin(3) = buffer.get(3)

      marshall(tid, Constants.TYPE_TRANSMIT, ByteString(bin) ++ exbp)
    }
  }

  class WsdTransmitPacket(override val tid: Int, val addr: Int, val exbp: BasicExbPacket) extends BasicWsdPacket

  //-----------------------------------------------------------------------------------------------

  object WsdSetPipeMatrixPacket {
    def apply(tid: Int, matrix: PipeMatrix): ByteString = toBinary(tid, matrix)
    def apply(bin: ByteString): WsdSetPipeMatrixPacket = fromBinary(bin)

    private def fromBinary(bin: ByteString) = {
      val raw = unmarshall(bin)


      def fbo(offset: Int): Int = {
        val arr = Array(
          raw.body(offset),
          raw.body(offset + 1),
          raw.body(offset + 2),
          raw.body(offset + 3)
        )
        val wrapped = ByteBuffer.wrap(arr)
        wrapped.getInt()
      }

      val matrix = PipeMatrix(
        fbo(0),
        fbo(4),
        fbo(8),
        fbo(12),
        fbo(16),
        fbo(20),
        fbo(24),
        fbo(28),
        fbo(32),
        fbo(36)
      )

      new WsdSetPipeMatrixPacket(raw.tid, matrix)
    }

    private def toBinary(tid: Int, matrix: PipeMatrix) =  {
      val bin: Array[Byte] = Array.fill(40)(0)

      def tbo(offset: Int, v: Int): Unit = {
        val buffer = ByteBuffer.allocate(4)
        buffer.putInt(v)
        bin(offset) = buffer.get(0)
        bin(offset + 1) = buffer.get(1)
        bin(offset + 2) = buffer.get(2)
        bin(offset + 3) = buffer.get(3)
      }

      tbo(0, matrix.p1targ)
      tbo(4, matrix.p1self)
      tbo(8, matrix.p2targ)
      tbo(12, matrix.p2self)
      tbo(16, matrix.p3targ)
      tbo(20, matrix.p3self)
      tbo(24, matrix.p4targ)
      tbo(28, matrix.p4self)
      tbo(32, matrix.p5targ)
      tbo(36, matrix.p5self)

      marshall(tid, Constants.WSD_TYPE_SET_PIPES_MATRIX, ByteString(bin))
    }
  }

  case class PipeMatrix(
    p1targ: Int,
    p1self: Int,
    p2targ: Int,
    p2self: Int,
    p3targ: Int,
    p3self: Int,
    p4targ: Int,
    p4self: Int,
    p5targ: Int,
    p5self: Int
  )

  class WsdSetPipeMatrixPacket(override val tid: Int, val matrix: PipeMatrix) extends BasicWsdPacket

  //-----------------------------------------------------------------------------------------------

  /** Broken wsd packet. It class uses if an attempt to parse the binary representation of the package failed */
  object WsdBrokenPacket {
    def apply(): WsdBrokenPacket = new WsdBrokenPacket(0)
  }

  class WsdBrokenPacket(override val tid: Int) extends BasicWsdPacket {}

  //-----------------------------------------------------------------------------------------------

  class WsdRawPacket(override val tid: Int, val `type`: Byte, val body: ByteString) extends BasicExbPacket

  //-----------------------------------------------------------------------------------------------
  abstract class BasicWsdPacket() {
    val tid: Int
  }

}



