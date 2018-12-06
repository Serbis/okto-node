package ru.serbis.okto.node.common

import java.nio.ByteBuffer

import akka.util.ByteString
import ru.serbis.okto.node.runtime.StreamControls

object ReachTypes {
  implicit class ReachByteString(self: ByteString) {
    def toHexString = self.foldLeft("")((a, v) => s"$a${v.toHexString.toUpperCase} ").dropRight(1)
    def toShort = ByteBuffer.wrap(self.toArray).getShort
    def toInt = ByteBuffer.wrap(self.toArray).getInt
    def toProto = com.google.protobuf.ByteString.copyFrom(self.toArray)
    def eoi: ByteString = self ++ ByteString(StreamControls.EOI)
    def eof: ByteString = self ++ ByteString(StreamControls.EOF)
    def eop: ByteString = self ++ ByteString(StreamControls.EOP)
    def exit(code: Int): ByteString = self ++ ByteString(Array(code.toByte))
    def prompt: ByteString = self ++ ByteString(StreamControls.PROMPT)
  }

  implicit class ReachProtoByteString(self: com.google.protobuf.ByteString) {
    def toAkka = ByteString(self.toByteArray)
  }

  implicit class ReachShort(self: Short) {
    def toBinary = ByteString(ByteBuffer.allocate(2).putShort(self).array())
  }

  implicit class ReachList[T](self: List[T]) {
    def tailOrEmpty = if (self.lengthCompare(1) <= 0) List.empty else self.tail
    def toSpacedString = self.foldLeft("")((a, v) => s"$a$v ").dropRight(1)
  }

  implicit class ReachSet[T](self: Set[T]) {
    def toSpacedString = self.foldLeft("")((a, v) => s"$a$v ").dropRight(1)
  }

  implicit class ReachVector[T](self: Vector[T]) {
    def tailOrEmpty = if (self.lengthCompare(1) <= 0) Vector.empty else self.tail
    def toSpacedString = self.foldLeft("")((a, v) => s"$a$v ").dropRight(1)
  }
}
