package external

import japgolly.tinytest._

/*
object SharedTests {
  import TestAuthor._

  def assertEq[A](a: A, e: A) = ()

//  def blaBla = group("blah blah blah") {
//    test("eg 1") {
//      assertEq(1 + 1, 2)
//    }
//    test("eg 2") {
//      assertEq(1 + 1, 2)
//    }
//  }.ignore

  def math = "test all the math" ~ {
    for (f <- List(1, 2, 6, 7, 8))
      s"$f" ~ assertEq(f + f, f * 2)
  }
}
*/

object Example extends TestSuite {
  def assertEq[A](a: A, e: A) = ()

  group("blah blah blah") {
    test("eg 1") {
      assertEq(1 + 1, 2)
    }
    test("eg 2") {
      assertEq(1 + 1, 2)
    }
  }

  "quick" ~ assertEq(1 + 1, 2)

  group("math") {
    for (f <- List(1, 2, 6, 7, 8))
      s"2x $f" ~ assertEq(f + f, f * 2)
  }

  lazy val h = TestGlobal.Resource("horse", Right((123, Proc.unit)))
  lazy val c = TestGlobal.Resource("cow", Left(OptionN.empty))

  h { i => s"horse = $i" ~ assertEq(i, 123) }
  c { i => s"cow = $i" ~ assertEq(i, 123) }

  //  addTest(SharedTests.blaBla)
  //  addTest(SharedTests.math)
}
