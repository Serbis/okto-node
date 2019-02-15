package ru.serbis.okto.node.runtime.senv

import akka.actor.ActorRef
import akka.stream.ActorMaterializer
import ru.serbis.okto.node.common.Env
import ru.serbis.okto.node.proxy.http.RealHttpProxy
import ru.serbis.okto.node.proxy.system.RealActorSystemProxy
import ru.serbis.okto.node.runtime.senv.vruntime.VRuntime
import ru.serbis.okto.node.runtime.senv.vstorage.VStorage


/** The env object of the execution environment of the script. Contains references to the main objects of interaction
  * of the script with the external environment.
  *
  * @param executor executor actor reference
  * @param stdInStream script standard input
  * @param stdOutStream script standard output
  */
class ScriptEnvInstance(executor: ActorRef, stdInStream: ActorRef, stdOutStream: ActorRef, env: Env) {
  val stdOut = new VStdOut(executor, stdOutStream)
  val stdIn = new VStdIn(executor, stdInStream)
  val scriptControl = new VScriptControl(executor)
  val runtime = new VRuntime(executor, env.scriptsRep)
  val bridge = new VBridge(env.serialBridge, env.rfBridge)
  val nsd = new VNsd(env.systemDaemon)
  val events = new VEvents(env.eventer, executor)
  val http = new VHttp(new RealHttpProxy()(env.system.get), ActorMaterializer.create(env.system.get))
  val storage = new VStorage(env.storageRep, new RealActorSystemProxy(env.system.get))
}
