package ru.serbis.okto.node.runtime

object StreamControls {
  val EOF = 0xFF.toByte //END OF STREAM
  val EOI = 0x17.toByte //END OF INPUT
  val EOP = 0x10.toByte //END OF PROGRAM
  val PROMPT = 0x05.toByte //PROMPT TO INPUT
  val BREAK = 0x06.toByte //CONNECTION BREAK
}
