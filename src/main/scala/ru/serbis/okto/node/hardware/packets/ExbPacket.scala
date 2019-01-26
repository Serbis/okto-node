package ru.serbis.okto.node.hardware.packets

import java.nio.ByteBuffer
import akka.util.ByteString


/** This functionality is used to work with ExB packages. The file contains objects for each type of package. Each such
  * object has two applicative functions. The first takes a ByteString as input and returns a package definition. The
  * second takes packet specific parameters and populates a ByteString. Similarly, in the head object ExbPacket, an
  * applicative function is defined that accepts a ByteString and returns a specific packet derived from the type field.
  * In all applicative functions that work with binary data, if the binary representation of the package is incorrect,
  * it will return the type WrongExbPacket.
  *
  * Attention: Partial packet binary applicative function, does not control packet struct and size, and may cause
  * ArrayIndexOutOfBoundsException if packet has incorrect size of structure
  *
  * Example:
  *   val bin = ExbCommandPacket(99, "abc")
  *   val packet = ExbCommandPacket(bin)
  *
  *   val packet2 = ExbPacket(bin)
  *
  */
object ExbPacket {
  object Constants {
    val PREAMBLE: Long =  0x3BAAAAAAAAAAAAAAL
    val PREAMBLE_SIZE =  8
    val HEADER_SIZE_FULL = PREAMBLE_SIZE + 7

    val TYPE_COMMAND: Byte = 0
    val TYPE_RESPONSE: Byte = 1
    val TYPE_ERROR: Byte = 2
  }

  def apply(bin: ByteString): BasicExbPacket = {
    bin(Constants.PREAMBLE_SIZE + 4) match {
      case Constants.TYPE_COMMAND =>
        if (bin.size < Constants.HEADER_SIZE_FULL) ExbBrokenPacket()
        else ExbCommandPacket(bin)
      case Constants.TYPE_RESPONSE => ExbResponsePacket(bin)
        if (bin.size < Constants.HEADER_SIZE_FULL) ExbBrokenPacket()
        else ExbResponsePacket(bin)
      case Constants.TYPE_ERROR =>
        if (bin.size < Constants.HEADER_SIZE_FULL + 4) ExbBrokenPacket()
        else ExbErrorPacket(bin)
      case _ => ExbBrokenPacket()
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

  def unmarshall(bin: ByteString): ExbRawPacket = {

    val arr = Array( //TID
      bin(8),
      bin(9),
      bin(10),
      bin(11)
    )
    val wrapped = ByteBuffer.wrap(arr)
    val tid = wrapped.getInt()

    val `type` = bin(12) //TYPE

    new ExbRawPacket(tid, `type`,bin.slice(15, bin.size))
  }

  //-----------------------------------------------------------------------------------------------

  object ExbErrorPacket {
    def apply(tid: Int, code: Int, message: String): ByteString = toBinary(tid, code, message)
    def apply(bin: ByteString): ExbErrorPacket = fromBinary(bin)

    private def fromBinary(bin: ByteString): ExbErrorPacket = {
      val raw = unmarshall(bin)

      val arr = Array(
        raw.body(0),
        raw.body(1),
        raw.body(2),
        raw.body(3)
      )

      val wrapped = ByteBuffer.wrap(arr)
      val code = wrapped.getInt()


      new ExbErrorPacket(raw.tid, code, raw.body.slice(4, raw.body.size).utf8String)
    }

    private def toBinary(tid: Int, code: Int, message: String): ByteString =  {
      val bin: Array[Byte] = Array.fill(4 + message.length)(0)

      val buffer = ByteBuffer.allocate(4) //PREAMBLE
      buffer.putInt(code)
      bin(0) = buffer.get(0)
      bin(1) = buffer.get(1)
      bin(2) = buffer.get(2)
      bin(3) = buffer.get(3)

      if (message.length > 0)
        System.arraycopy(ByteString(message).toArray, 0, bin, 4, message.length)

      marshall(tid, Constants.TYPE_ERROR, ByteString(bin))
    }
  }

  class ExbErrorPacket(override val tid: Int, var code: Int, val message: String) extends BasicExbPacket

  //-----------------------------------------------------------------------------------------------

  object ExbResponsePacket {
    def apply(tid: Int, response: String): ByteString = toBinary(tid, response)
    def apply(bin: ByteString): ExbResponsePacket = fromBinary(bin)

    private def fromBinary(bin: ByteString): ExbResponsePacket = {
      val raw = unmarshall(bin)
      new ExbResponsePacket(raw.tid, raw.body.utf8String)
    }

    private def toBinary(tid: Int, response: String): ByteString =  marshall(tid, Constants.TYPE_RESPONSE, ByteString(response))
  }

  class ExbResponsePacket(override val tid: Int, val response: String) extends BasicExbPacket

  //-----------------------------------------------------------------------------------------------

  object ExbCommandPacket {
    def apply(tid: Int, command: String): ByteString = toBinary(tid, command)
    def apply(bin: ByteString): ExbCommandPacket = fromBinary(bin)

    private def fromBinary(bin: ByteString): ExbCommandPacket = {
      val raw = unmarshall(bin)
      new ExbCommandPacket(raw.tid, raw.body.utf8String)
    }

    private def toBinary(tid: Int, command: String): ByteString =  marshall(tid, Constants.TYPE_COMMAND, ByteString(command))
  }

  class ExbCommandPacket(override val tid: Int, val command: String) extends BasicExbPacket

  //-----------------------------------------------------------------------------------------------

  object ExbBrokenPacket {
    def apply(): ExbBrokenPacket = new ExbBrokenPacket(0)
  }

  class ExbBrokenPacket(override val tid: Int) extends BasicExbPacket

  //-----------------------------------------------------------------------------------------------

  class ExbRawPacket(override val tid: Int, val `type`: Byte, val body: ByteString) extends BasicExbPacket

  //-----------------------------------------------------------------------------------------------

  abstract class BasicExbPacket() {
    val tid: Int
  }
}
