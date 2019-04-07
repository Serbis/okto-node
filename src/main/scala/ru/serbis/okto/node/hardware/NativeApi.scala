package ru.serbis.okto.node.hardware

object NativeApi {
  @native def serialOpen (device: Array[Byte], baud: Int): Int
  @native def serialClose (fd: Int)
  @native def serialPutchar (fd: Int, c: Byte)
  @native def serialPuts (fd: Int, s: Array[Byte])
  @native def serialGetchar (fd: Int): Int
  @native def serialReadExbPacket(sd: Int, timeout: Int): Array[Byte]
  @native def unixDomainConnect(path: Array[Byte]): Int
  @native def unixDomainReadChar(sd: Int): Int
  @native def unixDomainWrite(sd: Int, s: Array[Byte]): Int
  @native def unixDomainClose(sd: Int)
  @native def unixDomainReadWsdPacket(sd: Int, timeout: Int): Array[Byte]
  @native def unixDomainReadNsdPacket(sd: Int, timeout: Int): Array[Byte]
}
