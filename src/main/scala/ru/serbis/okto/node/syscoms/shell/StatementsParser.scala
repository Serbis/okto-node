package ru.serbis.okto.node.syscoms.shell

import org.parboiled2.{CharPredicate, Parser, ParserInput, Rule1}

/** Shell statements parser */
object StatementsParser {
  def apply(input: ParserInput) = new StatementsParser(input)

  trait Statement
  case class PipedNode(commands: Vector[CommandNode]) extends Statement
  case class CommandNode(name: String, args: Vector[String])
  case class IdentNode(value: String)
}

class StatementsParser (val input: ParserInput) extends Parser {
  import StatementsParser._

  def Flow: Rule1[Seq[Statement]] = rule {
    Statement.+ ~ EOI
  }

  def Statement = rule {
    Piped
  }

  def Piped = rule {
    oneOrMore(Command).separatedBy("|") ~> ((a: Any) => {
      val x = a.asInstanceOf[Vector[CommandNode]]
      PipedNode(x)
    })
  }

  // cmd1 arg1 'arg2 arg3'
  def Command = rule {
    (ws.? ~ Ident ~ ws.? ~ zeroOrMore(Arg | EscapedArg).separatedBy(" ") ~ ws.?) ~> ((a: IdentNode, args: Any) => {
      CommandNode(a.value, args.asInstanceOf[Seq[String]].toVector) //x.tail.map(v => v.value)
    })
  }

  def Arg = rule {
    Ident ~> (v => {
      v.value
    })
  }

  def EscapedArg = rule {
    "'" ~ capture(oneOrMore(EscapedArgChar)) ~ "'" ~> ((a: String) => {
      a
    })
  }

  def Ident = rule {
    capture(oneOrMore(IdentCharSet)) ~> IdentNode
  }

  def ws = rule { oneOrMore(" ") }

  def nl = rule { oneOrMore("\n") }

  def wsnl = rule { ws.? ~ nl.? }

  def stringChar: Rule1[String] = rule { escapedChar | capture(StringCharSet) }
  def reservedChar: Rule1[String] = rule { capture("\"") }
  def escapedChar: Rule1[String] = rule { """\""" ~ (capture(StringCharSet) | reservedChar) }


  lazy val IdentCharSet = InnerChar -- '|' -- ' ' -- '\''
  lazy val StringCharSet = InnerChar -- "\"" ++ ' '
  lazy val EscapedArgChar = InnerChar -- "'" ++ ' '
  lazy val InnerChar =  CharPredicate.Visible.++(CharPredicate('\u0400' to '\u04FF'))
}
