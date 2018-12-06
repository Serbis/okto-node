package ru.serbis.okto.node.daemon

import org.apache.commons.daemon._

abstract class AbstractApplicationDaemon extends Daemon {
  def application: ApplicationLifecycle

  def init(daemonContext: DaemonContext) = application.init(daemonContext.getArguments)

  def start() = application.start()

  def stop() = application.stop()

  def destroy() = application.stop()
}