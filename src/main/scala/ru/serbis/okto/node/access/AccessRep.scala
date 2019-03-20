package ru.serbis.okto.node.access

import java.io.File
import java.nio.file.StandardOpenOption
import java.util

import scala.concurrent.ExecutionContext.Implicits.global
import akka.actor.{Actor, Props, Stash}
import akka.util.ByteString
import com.typesafe.config.ConfigFactory
import ru.serbis.okto.node.access.AccessCredentials.{GroupCredentials, Permissions, UserCredentials}
import ru.serbis.okto.node.log.Logger.LogEntryQualifier
import ru.serbis.okto.node.log.StreamLogger
import ru.serbis.okto.node.proxy.files.FilesProxy
import akka.pattern.pipe
import ru.serbis.okto.node.common.ReachTypes.ReachList
import ru.serbis.okto.node.common.ReachTypes.ReachVector

import collection.JavaConverters._
import scala.annotation.tailrec
import scala.concurrent.Future

object AccessRep {

  def props(confName: String, filesProxy: FilesProxy, tm: Boolean = false) =
    Props(new AccessRep(confName, filesProxy, tm))

  object Commands {

    /** Add new user. May respond with Success, Exist, GroupNotExist, WriteError
      *
      * @param name user name
      * @param password user password
      * @param permissions user permissions
      * @param groups user groupss
      */
    case class AddUser(name: String, password: String, permissions: Vector[String], groups: Vector[String])

    /** Add new group. May respond with Success, Exist or WriteError
      *
      * @param name group name
      * @param permissions group permissions
      */
    case class AddGroup(name: String, permissions: Vector[String])

    /** Delete some user
      *
      * @param name user name
      */
    case class DelUser(name: String)

    /** Delete some group. This operation also remove deleted group from all users witch contain her
      *
      * @param name group name
      * @param recursive remove deleted group from all existed users
      */
    case class DelGroup(name: String, recursive: Boolean = true)

    /** Request for internal access config representation. Respond with AccessConfig */
    case object GetAccessConfig

    /** Return UserCredentials for specified user or NotExist
      *
      * @param userName user name for search
      */
    case class GetPermissionsDefinition(userName: String)


  }

  object Responses {

    /** Some operation was successfully completed */
    case object Success

    /** User or group already exists in the configuration */
    case object Exist

    /** User or group does not exists in the configuration */
    case object NotExist

    /** At add user operation, user has not existed groups
      *
      * @param name not existed group name
      */
    case class GroupNotExist(name: String)

    /** Some error was occurred at configuration write operation (config does not saved on the disk) */
    case class WriteError(ex: Throwable)

    case class UnknownPermission(perm: String)
  }

  object Definitions {
    case class AccessConfig(users: Vector[UserDefinition], groups: Vector[GroupDefinition])
    case class UserDefinition(name: String, password: String, salt: String, permissions: Vector[String], groups: Vector[String])
    case class GroupDefinition(name: String, permissions: Vector[String])
  }

  object Internals {
    case class ConfigText(text: String)
    case class ConfigReadingError(ex: Throwable)
  }
}

class AccessRep(confPath: String, filesProxy: FilesProxy, tm: Boolean) extends Actor with Stash with StreamLogger {
  import AccessRep.Commands._
  import AccessRep.Responses._
  import AccessRep.Internals._
  import AccessRep.Definitions._

  implicit val logQualifier = LogEntryQualifier("static")

  val cfgFp = new File(confPath).toPath
  
  var accessConfig: Option[AccessConfig] = None

  setLogSourceName(s"AccessRep*${self.path.name}")
  setLogKeys(Seq("AccessRep"))

  Future {
    try {
      self ! ConfigText(ByteString(filesProxy.readAllBytes(new File(confPath).toPath)).utf8String)
    } catch {
      case ex: Runnable => self ! ConfigReadingError(ex)
    }
  }


  def ioRecv: Receive = {
    case ConfigText(text) =>
      implicit val logQualifier = LogEntryQualifier("ConfigText")

      try {
        val lbCfg = ConfigFactory.parseString(text)
        val users = lbCfg.getList("users")
        val groups = lbCfg.getList("groups")
        val userDfs = users.unwrapped().asScala.map(v => {
          val conv = v.asInstanceOf[java.util.HashMap[String, Any]]
          val name = conv.get("name").asInstanceOf[String]
          val password = conv.get("password").asInstanceOf[String]
          val salt = conv.get("salt").asInstanceOf[String]
          val groups = conv.get("groups").asInstanceOf[util.ArrayList[String]].asScala
          val permissions = conv.get("permissions").asInstanceOf[util.ArrayList[String]].asScala
          val userDef = UserDefinition(name, password, salt, permissions.toVector, groups.toVector)
          userDef
        })

        val groupDfs = groups.unwrapped().asScala.map(v => {
          val conv = v.asInstanceOf[java.util.HashMap[String, Any]]
          val name = conv.get("name").asInstanceOf[String]
          val permissions = conv.get("permissions").asInstanceOf[util.ArrayList[String]].asScala
          val groupDef = GroupDefinition(name, permissions.toVector)
          groupDef
        })

        accessConfig = Some(AccessConfig(userDfs.toVector, groupDfs.toVector))
        logger.info("AccessRep was started")
      } catch { // NOT TESTABLE
        case ex: Throwable =>
          logger.fatal(s"Unable to parse access.conf file [ reason=${ex.getMessage} ]")
          Thread.sleep(3000)
          context.system.terminate()

      }

      context.become(normalRecv)
      unstashAll()

    // NOT TESTABLE
    case ConfigReadingError(ex) =>
      implicit val logQualifier = LogEntryQualifier("ConfigReadingError")

      logger.fatal(s"Unable to read access.conf file [ reason=${ex.getMessage} ]")
      Thread.sleep(3000)
      context.system.terminate()

    case _ => stash()
  }

  def normalRecv: Receive = {

    /** See the message description */
    case AddGroup(name, permissions) =>
      implicit val logQualifier = LogEntryQualifier("AddGroup")

      //Transform permissions texts to permissions object
      @tailrec
      def pex(permissions: Vector[String], acc: Vector[Permissions.Permission]): Either[String, Vector[Permissions.Permission]] = {
        if (permissions.isEmpty)
          Right(acc)
        else {
          val p = Permissions(permissions.head)
          if (p.isDefined)
            pex(permissions.tailOrEmpty, acc :+ p.get )
          else
            Left(permissions.head)
        }
      }

      val pexr = pex(permissions, Vector.empty)

      if (pexr.isRight) {
        val cfg = accessConfig.get
        val exist = cfg.groups.find(v => v.name == name)
        if (exist.isEmpty) {
          accessConfig = Some(cfg.copy(cfg.users, GroupDefinition(name, pexr.right.get.map(v => v.toString)) +: cfg.groups))
          logger.debug(s"New group was added [ name=$name, permissions=$permissions ]")
          writeConfig(Success)
        } else {
          logger.info(s"Unable to add new group, group already exist [ name=$name, permissions=$permissions ]")
          sender() ! Exist
        }
      } else {
        logger.info(s"Unable to add new group, unknown permission [ neperm=${pexr.left.get}, name=$name, permissions=$permissions ]")
        sender() ! UnknownPermission(pexr.left.get)
      }



    /** See the message description */
    case AddUser(name, password, permissions, groups) =>
      implicit val logQualifier = LogEntryQualifier("AddUser")

      val cfg = accessConfig.get

      //Find not existed groups
      @tailrec
      def gex(groups: Vector[String]): Option[String] = {
        if (groups.isEmpty)
          None
        else {
          if (cfg.groups.exists(v => v.name == groups.head))
            gex(groups.tailOrEmpty)
          else
            Some(groups.head)
        }
      }

      //Transform permissions texts to permissions object
      @tailrec
      def pex(permissions: Vector[String], acc: Vector[Permissions.Permission]): Either[String, Vector[Permissions.Permission]] = {
        if (permissions.isEmpty)
          Right(acc)
        else {
          val p = Permissions(permissions.head)
          if (p.isDefined)
            pex(permissions.tailOrEmpty, acc :+ p.get )
          else
            Left(permissions.head)
        }
      }

      val gexr = gex(groups)
      val pexr = pex(permissions, Vector.empty)

      if (pexr.isRight) {
        if (gexr.isEmpty) {
          val exist = cfg.users.find(v => v.name == name)
          if (exist.isEmpty) {
            val salt = if (tm) "xxx" else AccessCredentials.genSalt()
            val pwd = AccessCredentials.hashPassword(password, salt)
            accessConfig = Some(cfg.copy(UserDefinition(name, pwd, salt, permissions.map(v => v.toString), groups) +: cfg.users, cfg.groups))
            logger.debug(s"New user was added [ name=$name, password=***, salt=***, permissions=$permissions, groups=$groups ]")
            writeConfig(Success)
          } else {
            logger.info(s"Unable to add new user, user already exist [ name=$name, password=***, permissions=$permissions, groups=$groups ]")
            sender() ! Exist
          }
        } else {
          logger.info(s"Unable to add new user, user groups contain not exist group [ negroup=${gexr.get} name=$name, password=***, permissions=$permissions, groups=$groups ]")
          sender() ! GroupNotExist(gexr.get)
        }
      } else {
        logger.info(s"Unable to add new user, unknown permission [ neperm=${pexr.left.get} name=$name, password=***, permissions=$permissions, groups=$groups ]")
        sender() ! UnknownPermission(pexr.left.get)
      }


    /** See the message description */
    case DelGroup(name, recursive) =>
      implicit val logQualifier = LogEntryQualifier("DelGroup")

      val cfg = accessConfig.get
      val exist = cfg.groups.find(v => v.name == name)
      if (exist.isDefined) {
        val nUsers = if (recursive) cfg.users.map(v => v.copy(groups = v.groups.filter(m => m != name))) else cfg.users
        accessConfig = Some(cfg.copy(nUsers, cfg.groups.filter(v => v.name != name)))
        logger.debug(s"Group was deleted [ name=$name ]")
        writeConfig(Success)
      } else {
        logger.info(s"Unable to delete group, group does not exist [ name=$name ]")
        sender() ! NotExist
      }

    /** See the message description */
    case DelUser(name) =>
      implicit val logQualifier = LogEntryQualifier("DelUser")

      val cfg = accessConfig.get
      val exist = cfg.users.find(v => v.name == name)
      if (exist.isDefined) {
        accessConfig = Some(cfg.copy(cfg.users.filter(v => v.name != name), cfg.groups))
        logger.debug(s"User was deleted [ name=$name ]")
        writeConfig(Success)
      } else {
        logger.info(s"Unable to delete user, user does not exist [ name=$name ]")
        sender() ! NotExist
      }

    /** See the message description */
    case GetAccessConfig =>
      implicit val logQualifier = LogEntryQualifier("GetAccessConfig")

      logger.debug(s"Access config requested")
      sender() ! accessConfig.get

    /** See the message description */
    case GetPermissionsDefinition(name) =>
      val cfg = accessConfig.get
      val user = cfg.users.find(a => a.name == name)
      if (user.isDefined) {
        val groups = user.get.groups.foldLeft(Set.empty[GroupCredentials])((a, v) => {
          val g = cfg.groups.find(m => m.name == v)
          if (g.isDefined)
            a + GroupCredentials(g.get.name, g.get.permissions.map(m => Permissions(m).get).toSet)
          else
            a
        })
        logger.debug(s"User definition retrieved [ user=$user ]")
        sender() ! UserCredentials(user.get.name, user.get.password, user.get.salt, user.get.permissions.map(m => Permissions(m).get).toSet, groups)
      } else {
        logger.debug(s"User definition not found [ user=$user ]")
        sender() ! NotExist
      }

  }





  override def receive = ioRecv

  def writeConfig(msg: Any) = {
    context.become(ioRecv)
    
    Future {
      val c ="\""
      val users = accessConfig.get.users.foldLeft("")((a, v) => {
        val permissions = v.permissions.foldLeft("")((a, v) => s"$a$c$v$c, ").dropRight(2)
        val groups = v.groups.foldLeft("")((a, v) => s"$a$c$v$c, ").dropRight(2)
        a + s"  {\n    name: $c${v.name}$c\n    password: $c${v.password}$c\n    salt: $c${v.salt}$c\n    permissions: [$permissions]\n    groups: [$groups]\n  },\n"
      }).dropRight(2)
      
      val groups = accessConfig.get.groups.foldLeft("")((a, v) => {
        val permissions = v.permissions.foldLeft("")((a, v) => s"$a$c$v$c, ").dropRight(2)
        a + s"  {\n    name: $c${v.name}$c,\n    permissions: [$permissions]\n  },\n"
      }).dropRight(2)
      
      val text = s"users: [\n$users\n]\n\ngroups: [\n$groups\n]"

      try {
        filesProxy.write(cfgFp, ByteString(text).toArray, StandardOpenOption.TRUNCATE_EXISTING)
        context.become(normalRecv)
        unstashAll()
        msg
      } catch {
        case ex: Throwable =>
          context.become(normalRecv)
          unstashAll()
          WriteError(ex)
      }
    } pipeTo sender()
  }
}
