package ru.serbis.okto.node.runtime

import javax.script.{ScriptEngine, ScriptEngineManager}
import akka.actor.{Actor, Props}
import ru.serbis.okto.node.log.Logger.LogEntryQualifier
import ru.serbis.okto.node.log.StreamLogger
import scala.collection.mutable


/** The pool of virtual machines is javascript-nashorn. The task of the actor is to distribute runtime instances for the
  * needs of the application program execution system. The pool at startup has the base number of created runtime
  * defined by min. If there is a shortage of runtime in the pool, it can expand to max. When you free previously
  * reserved runtime above the min limit, they will be removed from the pool.
  */
object VmPool {

  /** @param max maximum pool size
    * @param min base pool size
    */
  def props(max: Int, min: Int) = Props(new VmPool(max, min))

  object Commands {

    /** Reserve new vm runtime instance. Respond with VmInstance message or PoolOverflow if pool size was
      * reach max instances size */
    case object Reserve

    /** Mark some runtime instance as free
      * @param vm runtime instance
      */
    case class Free(vm: ScriptEngine)

    /** Return pool size in PoolSize message */
    case object GetPoolSize
  }

  object Responses {
    case class VmInstance(vm: ScriptEngine)

    /** Response to GetPoolSize message
      *
      * @param size pool size
      */
    case class PoolSize(size: Int)

    /** Response for Reserve message */
    case object PoolOverflow
  }
}

class VmPool(max: Int, min: Int) extends Actor with StreamLogger {
  import VmPool.Commands._
  import VmPool.Responses._

  setLogSourceName(s"VmPool*${self.path.name}")
  setLogKeys(Seq("VmPool"))

  implicit val logQualifier = LogEntryQualifier("static")

  /** Vm's runtime pool */
  val pool = mutable.HashMap.empty[ScriptEngine, Boolean] //Bool isFree

  //Fill pool with new runtime instances to base
  (1 to min).foreach(_ => pool += new ScriptEngineManager().getEngineByName("nashorn") -> true)

  logger.info(s"VmPool actor is initialized. Created '$min' nashorn vm")

  override def receive = {

    /** See the message description */
    case Reserve =>
      implicit val logQualifier = LogEntryQualifier("Reserve")
      val vm = pool.find(v => v._2)
      if (vm.isDefined) {
        pool(vm.get._1) = false
        logger.debug(s"Vm instance '${vm.hashCode()}' was marked as reserved")
        sender() ! VmInstance(vm.get._1)
      } else {
        if (pool.size >= max) {
          logger.info(s"Unable to reserve vm instance. Pool size is reached max '$max' value")
          sender() ! PoolOverflow
        } else {
          val newVm = new ScriptEngineManager().getEngineByName("nashorn")
          pool += newVm -> false
          logger.debug(s"New vm instance '${newVm.hashCode()}' was injected to pool")
          logger.debug(s"Vm instance '${newVm.hashCode()}' was marked as reserved")
          sender() ! VmInstance(newVm)
        }
      }

    /** See the message description */
    case Free(vm) =>
      implicit val logQualifier = LogEntryQualifier("Free")
      val vmx = pool.find(v => v._1 == vm)
      if (vmx.isDefined) {
        logger.debug(s"Vm instance '${vm.hashCode()}' was marked as free")
        if (pool.size > min)
          pool -= vm
        else
          pool(vmx.get._1) = true
      }

    /** See the message description */
    case GetPoolSize =>
      sender() ! PoolSize(pool.size)
  }
}
