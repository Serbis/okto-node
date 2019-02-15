package ru.serbis.okto.node.events

import akka.util.ByteString
import ru.serbis.okto.node.common.ReachTypes.ReachByteString


abstract class SystemEvent {
  val eid: Int

  def logPaste: String
}

/** Hardware event representation
  *
  * @param eid event identifier, number witch unique identity some event
  * @param id unique event id generated by event source
  * @param addr network address from this event was received
  * @param confirmed confirmed event or not
  * @param data event payload
  */
case class HardwareEvent(override val eid: Int,  id: Int, addr: Int, confirmed: Boolean, data: ByteString) extends SystemEvent {
  override def logPaste = s"eid=$eid, id=$id, addr=$addr, confirmed=$confirmed, data=${data.toHexString}"
}
