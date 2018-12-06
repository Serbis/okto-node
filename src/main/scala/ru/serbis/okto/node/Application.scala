package ru.serbis.okto.node

import java.io.File

import akka.actor.ActorSystem
import com.typesafe.config.{Config, ConfigFactory}
import ru.serbis.okto.node.common.NodeUtils
import ru.serbis.okto.node.daemon.ApplicationLifecycle

import scala.util.Try

class Application extends ApplicationLifecycle {
  var started: Boolean = false
  var system: Option[ActorSystem] = None
  var config: Option[Config] = None
  var workDir: Option[String] = None

  override def init(args: Array[String]) = {
    workDir = Some( (Try { args(0) } recover { case _: Exception => "/etc/node/" }).get )
    //config = Some(ConfigFactory.parseFile(new File(s"${workDir.get}/node.conf")))
    // На данный момен никакх akka зависимых настроек в конфиграции нет
    // TD ошибка отсутствия файла
  }

  override def start() {
    if (!started) {
      NodeUtils.addJniPath(s"${workDir.get}/jni")
      System.setProperty("nashorn.args", "--language=es6")
      //println(s"JNI PATH - ${s"${workDir}/jni"}")
      started = true
      system = Some(ActorSystem("node"/*, config*/))
      val runner = system.get.actorOf(Runner.props)
      runner ! Runner.Commands.Exec(workDir.get)
    }
  }

  override def stop() {
    if (started) {
      started = false
      system.get.terminate()
    }
  }
}