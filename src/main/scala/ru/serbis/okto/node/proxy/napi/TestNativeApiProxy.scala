package ru.serbis.okto.node.proxy.napi

import akka.actor.ActorRef
import akka.pattern.ask
import scala.concurrent.duration._
import scala.concurrent.{Await, Future}

/** Test implementation of the proxy for hardware.NativeApi. For details see inherited trait */
object TestNativeApiProxy {
  object Actions {
    case class SerialOpen(device: Array[Byte], baud: Int)
    case class SerialClose(fd: Int)
    case class SerialPutchar(fd: Int, c: Byte)
    case class SerialPuts(fd: Int, s: Array[Byte])
    case class SerialGetchar(fd: Int)
    case class SerialReadExbPacket(fd: Int, timeout: Int)
    case class UnixDomainConnect(path: Array[Byte])
    case class UnixDomainReadChar(sd: Int)
    case class UnixDomainWrite(sd: Int, s: Array[Byte])
    case class UnixDomainClose(sd: Int)
    case class UnixDomainReadWsdPacket(sd: Int, timeout: Int)
    case class UnixDomainReadNsdPacket(sd: Int, timeout: Int)
  }

  object Predicts {
    case class Throw(ex: Throwable)
  }
}

class TestNativeApiProxy(tpRef: ActorRef) extends NativeApiProxy {
  import TestNativeApiProxy._

  override def serialOpen(device: Array[Byte], baud: Int) = {
    try {
      Await.result(tpRef.ask(Actions.SerialOpen(device, baud))(3 second), 3 second) match {
        case Predicts.Throw(ex) => throw ex
        case v: Int => v
      }
    } catch {
      case e: Exception => -1
    }
  }

  override def serialClose(fd: Int) = {
    try {
      Await.result(tpRef.ask(Actions.SerialClose(fd))(3 second), 3 second) match {
        case Predicts.Throw(ex) => throw ex
        case v: Unit =>
      }
    } catch {
      case e: Exception =>
    }
  }

  override def serialPutchar(fd: Int, c: Byte) = {
    try {
      Await.result(tpRef.ask(Actions.SerialPutchar(fd, c))(3 second), 3 second) match {
        case Predicts.Throw(ex) => throw ex
        case v: Unit =>
      }
    } catch {
      case e: Exception =>
    }
  }

  override def serialPuts(fd: Int, s: Array[Byte]) = {
    try {
      Await.result(tpRef.ask(Actions.SerialPuts(fd, s))(3 second), 3 second) match {
        case Predicts.Throw(ex) => throw ex
        case v: Int =>
      }
    } catch {
      case e: Exception =>
    }
  }

  override def serialGetchar(fd: Int) = {
    try {
      Await.result(tpRef.ask(Actions.SerialGetchar(fd))(3 second), 3 second) match {
        case Predicts.Throw(ex) => throw ex
        case v: Int => v
      }
    } catch {
      case e: Exception => 0
    }
  }

  override def serialReadExbPacket(fd: Int, timeout: Int) = {
    try {
      Await.result(tpRef.ask(Actions.SerialReadExbPacket(fd, timeout))(3 second), 3 second) match {
        case Predicts.Throw(ex) => throw ex
        case v: Array[Byte] => v
      }
    } catch {
      case e: Exception => Array.fill[Byte](100) (0)
    }
  }

  override def unixDomainConnect(path: Array[Byte]) = {
    try {
      Await.result(tpRef.ask(Actions.UnixDomainConnect(path))(3 second), 3 second) match {
        case Predicts.Throw(ex) => throw ex
        case v: Int => v
      }
    } catch {
      case e: Exception => 0
    }
  }

  override def unixDomainReadChar(sd: Int) = {
    try {
      Await.result(tpRef.ask(Actions.UnixDomainReadChar(sd))(3 second), 3 second) match {
        case Predicts.Throw(ex) => throw ex
        case v: Int => v
      }
    } catch {
      case e: Exception => 0
    }
  }

  override def unixDomainWrite(sd: Int, s: Array[Byte]) = {
    try {
      Await.result(tpRef.ask(Actions.UnixDomainWrite(sd, s))(3 second), 3 second) match {
        case Predicts.Throw(ex) => throw ex
        case v: Int => v
      }
    } catch {
      case e: Exception => 0
    }
  }

  override def unixDomainClose(sd: Int) = {
    try {
      Await.result(tpRef.ask(Actions.UnixDomainClose(sd))(3 second), 3 second) match {
        case Predicts.Throw(ex) => throw ex
        case v: Unit =>
      }
    } catch {
      case e: Exception =>
    }
  }

  override def unixDomainReadWsdPacket(sd: Int, timeout: Int) = {
    try {
      Await.result(tpRef.ask(Actions.UnixDomainReadWsdPacket(sd, timeout))(3 second), 3 second) match {
        case Predicts.Throw(ex) => throw ex
        case v: Array[Byte] => v
      }
    } catch {
      case e: Exception => Array.fill[Byte](100) (0)
    }
  }

  override def unixDomainReadNsdPacket(sd: Int, timeout: Int) = {
    try {
      Await.result(tpRef.ask(Actions.UnixDomainReadNsdPacket(sd, timeout))(3 second), 3 second) match {
        case Predicts.Throw(ex) => throw ex
        case v: Array[Byte] => v
      }
    } catch {
      case e: Exception => Array.fill[Byte](100) (0)
    }
  }
}
