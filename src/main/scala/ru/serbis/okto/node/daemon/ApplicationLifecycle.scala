package ru.serbis.okto.node.daemon

trait ApplicationLifecycle {
  def init(args: Array[String]): Unit
  def start(): Unit
  def stop(): Unit
}
