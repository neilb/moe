package org.moe.interpreter

import org.scalatest.FunSuite
import org.scalatest.matchers.ShouldMatchers

import org.moe.runtime._
import org.moe.ast._

import ClassMatchers._

class ArithmeticTestSuite
  extends FunSuite
  with InterpreterTestUtils
  with ShouldMatchers {
  test("... basic test with addition") {
    // 2 + 2
    val ast = wrapSimpleAST(
      List(
        MethodCallNode(
          IntLiteralNode(2),
          "+",
          List(IntLiteralNode(2))
        )
      )
    )
    val result = interpreter.eval(runtime, runtime.getRootEnv, ast)
    result.asInstanceOf[MoeIntObject].getNativeValue should equal (4)
  }

  test("... float + int") {
    // 2 + 2
    val ast = wrapSimpleAST(
      List(
        MethodCallNode(
          FloatLiteralNode(2.5),
          "+",
          List(IntLiteralNode(2))
        )
      )
    )
    val result = interpreter.eval(runtime, runtime.getRootEnv, ast)
    result.asInstanceOf[MoeFloatObject].getNativeValue should equal (4.5)
  }

  test("... int + float") {
    // 2 + 2
    val ast = wrapSimpleAST(
      List(
        MethodCallNode(
          IntLiteralNode(2),
          "+",
          List(FloatLiteralNode(2.5))
        )
      )
    )
    val result = interpreter.eval(runtime, runtime.getRootEnv, ast)
    result.asInstanceOf[MoeFloatObject].getNativeValue should equal (4.5)
  }

  test("... float + float") {
    // 2 + 2
    val ast = wrapSimpleAST(
      List(
        MethodCallNode(
          FloatLiteralNode(2.5),
          "+",
          List(FloatLiteralNode(2.5))
        )
      )
    )
    val result = interpreter.eval(runtime, runtime.getRootEnv, ast)
    result.asInstanceOf[MoeFloatObject].getNativeValue should equal (5.0)
  }
}