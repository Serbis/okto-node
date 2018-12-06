package ru.serbis.okto.node.daemon

import ru.serbis.okto.node.Application

class ApplicationDaemon() extends AbstractApplicationDaemon {
  var app: Option[Application] = None
  def application = {
    if (app.isEmpty)
      app = Some(new Application)

    app.get
  }
}
