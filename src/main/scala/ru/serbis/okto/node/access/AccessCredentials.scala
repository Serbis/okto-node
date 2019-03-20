package ru.serbis.okto.node.access
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

import akka.util.ByteString
import ru.serbis.okto.node.common.ReachTypes.ReachByteString

import scala.util.Random


object AccessCredentials {
  case class UserCredentials(user: String, password: String = "", salt: String = "", permissions: Set[Permissions.Permission] = Set.empty, groups: Set[GroupCredentials] = Set.empty)
  case class GroupCredentials(name: String, permissions: Set[Permissions.Permission])

  object Permissions extends Enumeration {
    type Permission = Value

    def apply(str: String): Option[Permissions.Permission] = str match {
      case "All" => Some(All)
      case "RunScripts" => Some(RunScripts)
      case "RunSystemCommands" => Some(RunSystemCommands)
      case _ => None
    }

    val All, RunScripts, RunSystemCommands = Value
  }

  def hashPassword(plain: String, salt: String): String = {
    val digest = MessageDigest.getInstance("SHA-512")
    val hash = ByteString(digest.digest((plain + salt).getBytes(StandardCharsets.UTF_8))).toHexStringMod(sep = "")
    hash
  }

  def genSalt(): String = {
    val arr = Array.fill(16)(Random.nextInt(255).toByte)
    ByteString(arr).toHexStringMod(sep = "")
  }

  def isHasPermission(credentials: UserCredentials, permission: Permissions.Permission): Boolean = {

    if (credentials.permissions.contains(permission))
      true
    else {
      if (credentials.groups.exists(v => v.permissions.contains(permission)))
        true
      else {
        if (credentials.permissions.contains(Permissions.All)){
          true
        } else {
          if (credentials.groups.exists(v => v.permissions.contains(Permissions.All))) {
            true
          } else {
            false
          }
        }
      }
    }
  }
}
