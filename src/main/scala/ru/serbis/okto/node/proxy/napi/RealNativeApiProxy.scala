package ru.serbis.okto.node.proxy.napi

import ru.serbis.okto.node.hardware.NativeApi


/** Production implementation of the proxy for hardware.NativeApi. For details see inherited trait */
class RealNativeApiProxy extends NativeApiProxy {
  override def serialOpen(device: Array[Byte], baud: Int) = NativeApi.serialOpen(device, baud)

  override def serialClose(fd: Int) = NativeApi.serialClose(fd)

  override def serialPutchar(fd: Int, c: Byte) = NativeApi.serialPutchar(fd, c)

  override def serialPuts(fd: Int, s: Array[Byte]) = NativeApi.serialPuts(fd, s)

  override def serialGetchar(fd: Int) = NativeApi.serialGetchar(fd)

  override def unixDomainConnect(path: Array[Byte]) = NativeApi.unixDomainConnect(path)

  override def unixDomainReadChar(sd: Int) = NativeApi.unixDomainReadChar(sd)

  override def unixDomainWrite(sd: Int, s: Array[Byte]) = NativeApi.unixDomainWrite(sd, s)

  override def unixDomainClose(sd: Int) = NativeApi.unixDomainClose(sd)

  override def serialReadExbPacket(fd: Int, timeout: Int) = NativeApi.serialReadExbPacket(fd, timeout)

  override def unixDomainReadWsdPacket(sd: Int, timeout: Int) = NativeApi.unixDomainReadWsdPacket(sd, timeout)
}
