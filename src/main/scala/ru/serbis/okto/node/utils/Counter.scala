package ru.serbis.okto.node.utils

object Counter {
  def apply(start: Int = 0): Counter = new Counter(start)
}

class Counter(var cnt: Int) {
  def get(): Int = {
    val prev = cnt
    cnt = cnt + 1
    prev
  }
}
