package ru.serbis.okto.node.reps

import java.io.File
import java.nio.file.{Files, StandardOpenOption}

import akka.actor.{Actor, Props, Stash, Timers}
import akka.pattern.pipe
import akka.util.ByteString
import ru.serbis.okto.node.log.Logger.LogEntryQualifier
import ru.serbis.okto.node.log.StreamLogger

import scala.collection.mutable
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.Future
import scala.concurrent.duration._

/** This repository is used to access the user scripts code located in a special folder on the disk. It supports the
  * cashing of the script code within the specified time interval, after which the cached data is deleted
  * (if the script is used frequently, it is in memory, if not, it is read from the disk). Also, the repository supports
  * forced cache reset, in case of changing the data on the disk. It is possible to create and remove scripts.
  */
object ScriptsRep {

  /** @param path path to the script folder
    * @param cacheTime time to live scripts in the cache is millis */
  def props(path: String, cacheTime: Long, cacheCleanInterval: Long, testMode: Boolean = false) =
    Props(new ScriptsRep(path, cacheTime, cacheCleanInterval, testMode))

  object Commands {

    /** Get script code. If it is cached, return cached version, else load it from disk (in async stash mode). Respond
      * with Script message or ScriptNotFound if file does not exist on the disk.
      *
      * @param name script name (with extension)
      */
    case class GetScript(name: String)

    /** Reset cached value with specified name if it is exist in cache
      *
      * @param name script name (with extension)
      */
    case class ResetCache(name: String)

    /** Create new file in the scripts directory. It is pseudo-asynchronous operation - all incoming commands that
      * works with the files, during this operation take place, will be stashed.
      *
      * @param name script name
      * @param code script code
      */
    case class Create(name: String, code: String)

    /** Remove the script file from the scripts directory. It is pseudo-asynchronous operation - all incoming commands
      * that works with the files, during this operation take place, will be stashed.
      *
      * @param name script name
      */
    case class Remove(name: String)
  }

  object Responses {

    /** Response for GetScript request
      *
      * @param code script program code
      */
    case class Script(code: String)

    /** Response for GetScript request */
    case object ScriptNotFound

    /**  Script file was created */
    case object Created

    /** Script file was removed */
    case object Removed

    /** Some io error during file writing operation*/
    case object WriteError
  }

  object Internals {

    /** This message sent to the self by cache cleaner timer */
    case object Clean

    /** The message sent by the io future at the end of the file download from the disk */
    case class Loaded(name: String, data: Option[String])

    /** Cache entry
      *
      * @param code script code
      * @param deadTime time after that entry will be removed from the cache
      */
    case class CacheEntry(code: String, deadTime: Long)
  }
}

class ScriptsRep(path: String, cacheTime: Long, cacheCleanInterval: Long, testMode: Boolean) extends Actor with StreamLogger with Stash with Timers {
  import ScriptsRep.Commands._
  import ScriptsRep.Internals._
  import ScriptsRep.Responses._

  setLogSourceName(s"ScriptsRep*${self.path.name}")
  setLogKeys(Seq("ScriptsRep"))

  implicit val logQualifier = LogEntryQualifier("static")

  /** Internal code cache */
  var cache = mutable.HashMap.empty[String, Option[CacheEntry]]

  /** Script directory locking flag */
  var dirLocked = false

  timers.startPeriodicTimer(0, Clean, cacheCleanInterval millis)

  logger.info("ScriptsRep actor is initialized")

  override def receive = {

    /** See the message description */
    case GetScript(name) =>
      implicit val logQualifier = LogEntryQualifier("GetScript")
      val cached = cache.get(name)
      if (cached.isDefined) {
        if (cached.get.isDefined) {
          val c = cached.get.get
          cache += name -> Some(c.copy(deadTime = System.currentTimeMillis() + cacheTime))
          logger.debug(s"Read script file '$name' from the cache")
          sender() ! Script(cached.get.get.code)
        } else {
          cache -= name
          logger.warning(s"Script file '$name' does not exist in the location '$path'")
          sender() ! ScriptNotFound
        }
      } else {
        if (dirLocked) {
          stash()
        } else {
          stash()
          pipe (Future {
            val file = new File(s"$path/$name").toPath
            if (Files.exists(file)) {
              try {
                logger.debug(s"Script file '$name' loaded from the disk")
                self ! Loaded(name, Some(ByteString(Files.readAllBytes(file)).utf8String))
              } catch {
                case e: Throwable => //NOT TESTABLE
                  logger.warning(s"Script file '$name' exist on the disk, but may not be read because '${e.getMessage}'")
                  self ! Loaded(name, None)
              }
            } else {
              self ! Loaded(name, None)
            }
          }) to self
        }
      }

    /** See the message description */
    case ResetCache(name) =>
      logger.debug(s"Cached script entry with name '$name' was reset")
      cache -= name

    /** See the message description */
    case Loaded(name, data) =>
      if (data.isDefined)
        cache += name -> Some(CacheEntry(data.get, System.currentTimeMillis() + cacheTime))
      else
        cache += name -> None
      unstashAll()

    /** See the message description */
    case Create(name, code) =>
      implicit val logQualifier = LogEntryQualifier("Create")

      val orig = sender()
      dirLocked = true
      Future {
        val file = new File(s"$path/$name.js").toPath
        try {
          if (testMode) Thread.sleep(2000)
          Files.deleteIfExists(file)
          Files.createFile(file)
          Files.write(file, ByteString(code).toArray, StandardOpenOption.TRUNCATE_EXISTING)
          logger.debug(s"Script file '$name.js' was created")
          if (cache.contains(s"$name.js"))
            self ! ResetCache(s"$name.js")
          orig ! Created
        } catch {
          case e: Throwable => //NOT TESTABLE
            logger.error(s"Script file '$name.js' create error. Reason: ${e.getMessage}")
            orig ! WriteError
        }

        dirLocked = false
        unstashAll()
      }

    /** See the message description */
    case Remove(name) =>
      implicit val logQualifier = LogEntryQualifier("Remove")

      val orig = sender()
      dirLocked = true
      Future {
        val file = new File(s"$path/$name.js").toPath
        try {
          Files.deleteIfExists(file)
          logger.debug(s"Script file '$name.js' was removed")
          orig ! Removed
        } catch {
          case e: Throwable => //NOT TESTABLE
            logger.error(s"Script file '$name.js' remove error. Reason: ${e.getMessage}")
            orig ! WriteError
        }

        dirLocked = false
        unstashAll()
      }

    /** See the message description */
    case Clean =>
      cache = cache.filter(v => {
        if (v._2.isDefined) {
          val m = System.currentTimeMillis()
          if (v._2.get.deadTime > m) {
            true
          } else {
            logger.debug(s"Script with name '${v._1}' was removed from the cache")
            false
          }
        } else {
          true
        }
      })
  }
}