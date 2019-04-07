package ru.serbis.okto.node.proxy.napi

import java.io.{File, FileInputStream, FileOutputStream, RandomAccessFile}
import java.nio.file.attribute.FileAttribute
import java.nio.file.{CopyOption, LinkOption, OpenOption, Path}

import akka.NotUsed
import akka.stream.IOResult
import akka.stream.scaladsl.Source
import akka.util.ByteString

import scala.concurrent.Future

/** Proxy for hardware.NativeApi. This system is used to create end-to-end testing of jni operations at the unit test
  * level. The principles of this proxy in the following. This trait has two implementations - test and real. Real
  * implementation duplicates the corresponding calls from the NativeApi object. The test implementation is used for testing
  * to intercept io operations and works as follows. The test implementation constructor has the reference of some actor that
  * will intercept requests, usually TestProbe. When calling any target method, the code of this method makes the ask call
  * to this actor by passing the action case class that defines the given method and its parameters. The interceptor actor
  * checks the received message for correctness, and then responds with a result that should return the target method. At
  * the same time, the interceptor can respond with a special message Throw, upon receipt of which the proxy initiates an
  * throw of the exception specified in the body message. All that remains to be done in practice is to transfer the
  * necessary implementation of the proxy to the actor who is going to use the file io operations.
  *
  * @version 2
  */
trait NativeApiProxy {
  def serialOpen (device: Array[Byte], baud: Int): Int
  def serialClose (fd: Int)
  def serialPutchar (fd: Int, c: Byte)
  def serialPuts (fd: Int, s: Array[Byte])
  def serialGetchar (fd: Int): Int
  def serialReadExbPacket(sd: Int, timeout: Int): Array[Byte]
  def unixDomainConnect(path: Array[Byte]): Int
  def unixDomainReadChar(sd: Int): Int
  def unixDomainWrite(sd: Int, s: Array[Byte]): Int
  def unixDomainClose(sd: Int)
  def unixDomainReadWsdPacket(sd: Int, timeout: Int): Array[Byte]
  def unixDomainReadNsdPacket(sd: Int, timeout: Int): Array[Byte]
}
