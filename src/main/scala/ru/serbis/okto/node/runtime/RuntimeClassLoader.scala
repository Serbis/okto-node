package ru.serbis.okto.node.runtime

import java.net.{URL, URLClassLoader}
import akka.actor.{Actor, Props}

object RuntimeClassLoader {
  def props(paths: Array[URL]) = Props(new RuntimeClassLoader(paths))

  object Commands {
    case class LoadClass(name: String)
  }

  object Responses {
    case class LoadedClass(clazz: Class[Any])
    case object ClassNotFound
  }
}

class RuntimeClassLoader(paths: Array[URL]) extends Actor {

  val classLoader = new URLClassLoader(paths)

  override def receive = {
    case 0 => 0
  }
}
