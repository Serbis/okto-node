package ru.serbis.okto.node.runtime.senv

import akka.actor.ActorRef

/** Abstract shell class. It implements functions common to all kinds of shells.
  *
  * @param process shell process reference
  * @param stdIn shell standard input stream
  * @param stdOut shell standard output stream
  */
abstract class VShell(val process: ActorRef, val stdIn: ActorRef, val stdOut: ActorRef) {
  def expectPrompt(timeout: Int): Boolean
  def expectEof(timeout: Int): Boolean
  def write(st: String): Boolean
  def read(timeout: Int): String
  def close()
}