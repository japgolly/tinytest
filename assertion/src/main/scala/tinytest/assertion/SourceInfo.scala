package tinytest.assertion

import scala.language.implicitConversions
import scala.quoted._
import scala.quoted.autolift._
import scala.tasty._

package AsClass {

  final class SourceInfo(val lineNo: Int)

  object SourceInfo {

    def unsafe(i: Int): SourceInfo =
      new SourceInfo(i)

    inline delegate for SourceInfo = ${impl}

    private def impl given (r: Reflection): Expr[SourceInfo] = {
      import r._
      '{unsafe(${rootPosition.startLine})}
    }
  }
}

// ====================================================================================

package AsOpaqueType {

  opaque type SourceInfo = Int

  object SourceInfo {

    delegate Ops {
      inline def (s: SourceInfo) lineNo: Int = s
    }

    inline def unsafe(i: Int): SourceInfo =
      i

    inline delegate bug for SourceInfo = ${impl}

    private def impl given (r: Reflection): Expr[SourceInfo] = {
      import r._
      '{unsafe(${rootPosition.startLine})}
    }
  }
}
