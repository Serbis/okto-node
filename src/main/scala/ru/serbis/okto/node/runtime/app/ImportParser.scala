package ru.serbis.okto.node.runtime.app

import org.parboiled2.{CharPredicate, Parser, ParserInput, Rule1}

/** Import statement parser */
object ImportParser {
  def apply(input: ParserInput) = new ImportParser(input)

  case class ImportNode(targets: List[TargetNode], from: String)
  case class TargetNode(ns: String, as: String)
  case class IdentNode(value: String)

  case class BraceElemNode(target: String, as: String)
}

//import { foo as x, bar as y } from "moda";
//import * as z from "modb";
class ImportParser (val input: ParserInput) extends Parser {
  import ImportParser._

  def Flow: Rule1[Seq[ImportNode]] = rule {
    Import.+ ~ EOI
  }

  def Import = rule {
    ("import" ~ ws ~ (AstrixImport | BraceImport) ~ ws ~ "from" ~ ws ~ "\"" ~ Ident ~ "\"" ~ ws.? ~ ";") ~> ((im: Any, from: IdentNode) => {
      im match {
        case x: TargetNode =>
          ImportNode(List(x), from.value)
        case x: Seq[TargetNode] =>
          ImportNode(x.toList, from.value)
      }
    })
  }

  def AstrixImport: Rule1[TargetNode] = rule {
    ws.? ~ "*" ~ ws ~ "as" ~ ws ~ Ident ~> ((as: IdentNode) => {
      TargetNode("*", as.value)
    })
  }

  def BraceImport = rule {
    "{" ~ ws.? ~ (BraceElem * ",")  ~ ws.? ~ "}" ~> ((l: Any) => {
      l.asInstanceOf[Seq[BraceElemNode]].map(v => TargetNode(v.target, v.as))
    })
  }

  def BraceElem = rule {
    (ws.? ~ Ident ~ ws ~ "as" ~ ws ~ Ident ~ ws.?) ~> ((target: IdentNode, as: IdentNode) => {
      BraceElemNode(target.value, as.value)
    })
  }

  def Ident = rule {
    capture(oneOrMore(IdentCharSet)) ~> IdentNode
  }

  def ws = rule { oneOrMore(" ") }

  lazy val IdentCharSet = InnerChar -- "\"" -- ","
  lazy val InnerChar =  CharPredicate.Visible.++(CharPredicate('\u0400' to '\u04FF'))
}
