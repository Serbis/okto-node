package ru.serbis.okto.node.hardware.packets

import java.nio.ByteBuffer
import akka.util.ByteString

/** This functionality is used to work with NSD packages. The file contains objects for each type of package. Each such
  * object has two applicative functions. The first takes a ByteString as input and returns a package definition. The
  * second takes packet specific parameters and populates a ByteString. Similarly, in the head object WsdPacket, an
  * applicative function is defined that accepts a ByteString and returns a specific packet derived from the type field.
  *
  * In all applicative functions that work with binary data, if the binary representation of the package is incorrect,
  * it will return the type WrongWsdPacket.
  *
  */
object NsdPacket {
  object Constants {
    val PREAMBLE: Long =  0x3FAAAAAAAAAAAAAAL
    val PREAMBLE_SIZE =  8
    val HEADER_SIZE_FULL = PREAMBLE_SIZE + 7

    val NSD_TYPE_CMD: Byte =  0
    val NSD_TYPE_RESULT: Byte = 1
    val NSD_TYPE_ERROR: Byte = 2


    val ERROR_BROKEN_PIPE_MATRIX = 1000
  }

  def apply(bin: ByteString): BasicNsdPacket = {
    bin(Constants.PREAMBLE_SIZE + 4) match {
      case Constants.NSD_TYPE_RESULT =>
        if (bin.size < Constants.HEADER_SIZE_FULL) NsdBrokenPacket()
        else NsdResultPacket(bin)
      case Constants.NSD_TYPE_ERROR =>
        if (bin.size < Constants.HEADER_SIZE_FULL + 4) NsdBrokenPacket()
        else NsdErrorPacket(bin)
      case Constants.NSD_TYPE_CMD =>
        if (bin.size < Constants.HEADER_SIZE_FULL) NsdBrokenPacket()
        else NsdCmdPacket(bin)
      case _ => NsdBrokenPacket()
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

  def unmarshall(bin: ByteString): NsdRawPacket = {

    val arr = Array( //TID
      bin(8),
      bin(9),
      bin(10),
      bin(11)
    )
    val wrapped = ByteBuffer.wrap(arr)
    val tid = wrapped.getInt()

    val `type` = bin(12) //TYPE

    new NsdRawPacket(tid, `type`,bin.slice(15, bin.size))
  }



  //-----------------------------------------------------------------------------------------------

  object NsdErrorPacket {
    def apply(tid: Int, code: Int, message: String): ByteString = toBinary(tid, code, message)
    def apply(bin: ByteString): NsdErrorPacket = fromBinary(bin)

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

      new NsdErrorPacket(raw.tid, code, raw.body.slice(4, raw.body.size).utf8String)
    }

    private def toBinary(tid: Int, code: Int, message: String) =  {
      val bin: Array[Byte] = Array.fill(4)(0)

      val buffer = ByteBuffer.allocate(4)
      buffer.putInt(code)
      bin(0) = buffer.get(0)
      bin(1) = buffer.get(1)
      bin(2) = buffer.get(2)
      bin(3) = buffer.get(3)

      marshall(tid, Constants.NSD_TYPE_ERROR, ByteString(bin) ++ ByteString(message))
    }
  }

  class NsdErrorPacket(override val tid: Int, val code: Int, val message: String) extends BasicNsdPacket

  //-----------------------------------------------------------------------------------------------

  object NsdResultPacket {
    def apply(tid: Int,  body: ByteString): ByteString = toBinary(tid, body)
    def apply(bin: ByteString): NsdResultPacket = fromBinary(bin)

    private def fromBinary(bin: ByteString) = {
      val raw = unmarshall(bin)

      new NsdResultPacket(raw.tid, raw.body)
    }

    private def toBinary(tid: Int, body: ByteString) =  {
      marshall(tid, Constants.NSD_TYPE_RESULT, body)
    }
  }

  class NsdResultPacket(override val tid: Int, val body: ByteString) extends BasicNsdPacket

  //-----------------------------------------------------------------------------------------------

  object NsdCmdPacket {
    def apply(tid: Int, cmd: ByteString): ByteString = toBinary(tid, cmd)
    def apply(bin: ByteString): NsdCmdPacket = fromBinary(bin)

    private def fromBinary(bin: ByteString) = {
      val raw = unmarshall(bin)

      new NsdCmdPacket(raw.tid, raw.body)
    }

    private def toBinary(tid: Int, cmd: ByteString) =  {
      val bin: Array[Byte] = Array.fill(4)(0)


      marshall(tid, Constants.NSD_TYPE_CMD, cmd)
    }
  }

  class NsdCmdPacket(override val tid: Int, val cmd: ByteString) extends BasicNsdPacket

  //-----------------------------------------------------------------------------------------------

  /** Broken wsd packet. It class uses if an attempt to parse the binary representation of the package failed */
  object NsdBrokenPacket {
    def apply(): NsdBrokenPacket = new NsdBrokenPacket(0)
  }

  class NsdBrokenPacket(override val tid: Int) extends BasicNsdPacket {}

  //-----------------------------------------------------------------------------------------------

  class NsdRawPacket(override val tid: Int, val `type`: Byte, val body: ByteString) extends BasicNsdPacket

  //-----------------------------------------------------------------------------------------------
  abstract class BasicNsdPacket() {
    val tid: Int
  }

}



