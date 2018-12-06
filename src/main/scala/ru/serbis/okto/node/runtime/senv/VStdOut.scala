package ru.serbis.okto.node.runtime.senv

import akka.actor.ActorRef
import akka.util.ByteString
import ru.serbis.okto.node.runtime.{AppCmdExecutor, StreamControls, Stream}

/** The class to interact with the standard output of the program
  *
  * @param executor executor actor reference
  */
class VStdOut(executor: ActorRef, stdOut: ActorRef) {

  /** Writes a string to standard output, appending the EOI character to the end.
    *
    * @param str input string
    */
  def write(str: String) = {
    if (str == "P0")
      println("x")
    //TODO [1] таймут записи в стандартный ввывод
    val data = /*if (eoi)*/ ByteString(str) ++ ByteString(Array(StreamControls.EOI))/* else ByteString(str)*/
    //executor.tell(AppCmdExecutor.Commands.StdOutWrite(data), ActorRef.noSender)
    stdOut.tell(Stream.Commands.WriteWrapped(data), ActorRef.noSender)
  }

  def writeControlChar(char: Int): Boolean = {
    //TODO [1] таймут записи в стандартный ввывод
    stdOut.tell(Stream.Commands.WriteWrapped(ByteString(Array(char.toByte))), ActorRef.noSender)
    true
  }
}