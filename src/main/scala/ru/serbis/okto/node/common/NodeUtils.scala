package ru.serbis.okto.node.common

import java.io.{File, IOException}

object NodeUtils {
  def addJniPath(s: String): Unit = {
    try { // This enables the java.library.path to be modified at runtime
      val field = classOf[ClassLoader].getDeclaredField("usr_paths")
      field.setAccessible(true)
      val paths = field.get(null).asInstanceOf[Array[String]]
      var i = 0
      while ( {
        i < paths.length
      }) {
        if (s == paths(i)) return

        {
          i += 1; i - 1
        }
      }
      val tmp = new Array[String](paths.length + 1)
      System.arraycopy(paths, 0, tmp, 0, paths.length)
      tmp(paths.length) = s
      field.set(null, tmp)
      System.setProperty("java.library.path", System.getProperty("java.library.path") + File.pathSeparator + s)
    } catch {
      case e: IllegalAccessException =>
        throw new IOException("Failed to get permissions to set library path")
      case e: NoSuchFieldException =>
        throw new IOException("Failed to get field handle to set library path")
    }
  }

  def getOptions(args: Vector[String]) = {
    val arglist = args.toList
    type OptionMap = Map[String, String]

    def nextOption(map : OptionMap, list: List[String]) : OptionMap = {
      list match {
        case Nil => map
        case _ :: Nil => map
        case v :: value :: tail => nextOption(map ++ Map(v -> value), tail)
      }
    }

    nextOption(Map(),arglist)
  }
}
