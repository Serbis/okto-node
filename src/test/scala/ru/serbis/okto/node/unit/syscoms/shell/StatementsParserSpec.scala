package ru.serbis.okto.node.unit.syscoms.shell

import org.scalatest.{Matchers, WordSpecLike}
import ru.serbis.okto.node.syscoms.shell.StatementsParser
import ru.serbis.okto.node.syscoms.shell.StatementsParser._

class StatementsParserSpec  extends WordSpecLike with Matchers {
  "StatementsParserSpec" must {
    "Produce correct ast for - 'cmd'" in {
      new StatementsParser("cmd").Flow.run().get shouldEqual Vector(PipedNode(Vector(CommandNode("cmd", Vector.empty))))
      new StatementsParser("  cmd").Flow.run().get shouldEqual Vector(PipedNode(Vector(CommandNode("cmd", Vector.empty))))
      new StatementsParser("cmd  ").Flow.run().get shouldEqual Vector(PipedNode(Vector(CommandNode("cmd", Vector.empty))))
      new StatementsParser("  cmd  ").Flow.run().get shouldEqual Vector(PipedNode(Vector(CommandNode("cmd", Vector.empty))))
    }

    "Produce correct ast for - 'cmd1 arg1 arg2 arg3'" in {
      new StatementsParser("cmd1 arg1 arg2 arg3").Flow.run().get shouldEqual Vector(PipedNode(Vector(CommandNode("cmd1", Vector("arg1", "arg2", "arg3")))))
      new StatementsParser("  cmd1 arg1 arg2 arg3").Flow.run().get shouldEqual Vector(PipedNode(Vector(CommandNode("cmd1", Vector("arg1", "arg2", "arg3")))))
      new StatementsParser("cmd1 arg1 arg2 arg3  ").Flow.run().get shouldEqual Vector(PipedNode(Vector(CommandNode("cmd1", Vector("arg1", "arg2", "arg3")))))
      new StatementsParser("  cmd1 arg1 arg2 arg3  ").Flow.run().get shouldEqual Vector(PipedNode(Vector(CommandNode("cmd1", Vector("arg1", "arg2", "arg3")))))
      new StatementsParser("cmd1  arg1 arg2 arg3").Flow.run().get shouldEqual Vector(PipedNode(Vector(CommandNode("cmd1", Vector("arg1", "arg2", "arg3")))))
    }

    "Produce correct ast for - 'cmd1 | cmd2'" in {
      new StatementsParser("cmd1 | cmd2").Flow.run().get shouldEqual Vector(PipedNode(Vector(CommandNode("cmd1", Vector.empty), CommandNode("cmd2", Vector.empty))))
      new StatementsParser(" cmd1 | cmd2").Flow.run().get shouldEqual Vector(PipedNode(Vector(CommandNode("cmd1", Vector.empty), CommandNode("cmd2", Vector.empty))))
      new StatementsParser("cmd1 | cmd2 ").Flow.run().get shouldEqual Vector(PipedNode(Vector(CommandNode("cmd1", Vector.empty), CommandNode("cmd2", Vector.empty))))
      new StatementsParser(" cmd1 | cmd2 ").Flow.run().get shouldEqual Vector(PipedNode(Vector(CommandNode("cmd1", Vector.empty), CommandNode("cmd2", Vector.empty))))
      new StatementsParser("cmd1  | cmd2").Flow.run().get shouldEqual Vector(PipedNode(Vector(CommandNode("cmd1", Vector.empty), CommandNode("cmd2", Vector.empty))))
      new StatementsParser("cmd1 |  cmd2").Flow.run().get shouldEqual Vector(PipedNode(Vector(CommandNode("cmd1", Vector.empty), CommandNode("cmd2", Vector.empty))))
      new StatementsParser("cmd1  |  cmd2").Flow.run().get shouldEqual Vector(PipedNode(Vector(CommandNode("cmd1", Vector.empty), CommandNode("cmd2", Vector.empty))))
    }

    "Produce correct ast for - 'cmd1 arg1 | cmd2'" in {
      new StatementsParser("cmd1 arg1 | cmd2").Flow.run().get shouldEqual Vector(PipedNode(Vector(CommandNode("cmd1", Vector("arg1")), CommandNode("cmd2", Vector.empty))))
      new StatementsParser(" cmd1 arg1 | cmd2").Flow.run().get shouldEqual Vector(PipedNode(Vector(CommandNode("cmd1", Vector("arg1")), CommandNode("cmd2", Vector.empty))))
      new StatementsParser("cmd1 arg1 | cmd2 ").Flow.run().get shouldEqual Vector(PipedNode(Vector(CommandNode("cmd1", Vector("arg1")), CommandNode("cmd2", Vector.empty))))
      new StatementsParser(" cmd1 arg1 | cmd2 ").Flow.run().get shouldEqual Vector(PipedNode(Vector(CommandNode("cmd1", Vector("arg1")), CommandNode("cmd2", Vector.empty))))
      new StatementsParser("cmd1 arg1  | cmd2").Flow.run().get shouldEqual Vector(PipedNode(Vector(CommandNode("cmd1", Vector("arg1")), CommandNode("cmd2", Vector.empty))))
      new StatementsParser("cmd1 arg1 |  cmd2").Flow.run().get shouldEqual Vector(PipedNode(Vector(CommandNode("cmd1", Vector("arg1")), CommandNode("cmd2", Vector.empty))))
      new StatementsParser("cmd1 arg1  |  cmd2").Flow.run().get shouldEqual Vector(PipedNode(Vector(CommandNode("cmd1", Vector("arg1")), CommandNode("cmd2", Vector.empty))))
    }

    "Produce correct ast for - 'cmd1 | cmd2 arg1'" in {
      new StatementsParser("cmd1 | cmd2 arg1").Flow.run().get shouldEqual Vector(PipedNode(Vector(CommandNode("cmd1", Vector.empty), CommandNode("cmd2", Vector("arg1")))))
      new StatementsParser(" cmd1 | cmd2 arg1").Flow.run().get shouldEqual Vector(PipedNode(Vector(CommandNode("cmd1", Vector.empty), CommandNode("cmd2", Vector("arg1")))))
      new StatementsParser("cmd1 | cmd2 arg1 ").Flow.run().get shouldEqual Vector(PipedNode(Vector(CommandNode("cmd1", Vector.empty), CommandNode("cmd2", Vector("arg1")))))
      new StatementsParser(" cmd1 | cmd2 arg1 ").Flow.run().get shouldEqual Vector(PipedNode(Vector(CommandNode("cmd1", Vector.empty), CommandNode("cmd2", Vector("arg1")))))
      new StatementsParser("cmd1  | cmd2 arg1").Flow.run().get shouldEqual Vector(PipedNode(Vector(CommandNode("cmd1", Vector.empty), CommandNode("cmd2", Vector("arg1")))))
      new StatementsParser("cmd1 |  cmd2 arg1").Flow.run().get shouldEqual Vector(PipedNode(Vector(CommandNode("cmd1", Vector.empty), CommandNode("cmd2", Vector("arg1")))))
      new StatementsParser("cmd1  |  cmd2 arg1").Flow.run().get shouldEqual Vector(PipedNode(Vector(CommandNode("cmd1", Vector.empty), CommandNode("cmd2", Vector("arg1")))))
    }

    "Produce correct ast for - 'cmd1 arg1 | cmd2 arg1'" in {
      new StatementsParser("cmd1 arg1 | cmd2 arg1").Flow.run().get shouldEqual Vector(PipedNode(Vector(CommandNode("cmd1", Vector("arg1")), CommandNode("cmd2", Vector("arg1")))))
      new StatementsParser(" cmd1 arg1 | cmd2 arg1").Flow.run().get shouldEqual Vector(PipedNode(Vector(CommandNode("cmd1", Vector("arg1")), CommandNode("cmd2", Vector("arg1")))))
      new StatementsParser("cmd1 arg1 | cmd2 arg1 ").Flow.run().get shouldEqual Vector(PipedNode(Vector(CommandNode("cmd1", Vector("arg1")), CommandNode("cmd2", Vector("arg1")))))
      new StatementsParser(" cmd1 arg1 | cmd2 arg1 ").Flow.run().get shouldEqual Vector(PipedNode(Vector(CommandNode("cmd1", Vector("arg1")), CommandNode("cmd2", Vector("arg1")))))
      new StatementsParser("cmd1 arg1  | cmd2 arg1").Flow.run().get shouldEqual Vector(PipedNode(Vector(CommandNode("cmd1", Vector("arg1")), CommandNode("cmd2", Vector("arg1")))))
      new StatementsParser("cmd1 arg1 |  cmd2 arg1").Flow.run().get shouldEqual Vector(PipedNode(Vector(CommandNode("cmd1", Vector("arg1")), CommandNode("cmd2", Vector("arg1")))))
      new StatementsParser("cmd1 arg1  |  cmd2 arg1").Flow.run().get shouldEqual Vector(PipedNode(Vector(CommandNode("cmd1", Vector("arg1")), CommandNode("cmd2", Vector("arg1")))))
    }
  }
}
