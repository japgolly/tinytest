package external

import japgolly.tinytest._

object SharedTests extends TestAuthor {

  def assertEq[A](a: A, e: A) = ()

  def blaBla = skip -> test("shared") {
    test("eg 1") {
      assertEq(1 + 1, 2)
    }
    test("eg 2") {
      assertEq(1 + 1, 2)
    }
  }

  def math = "sharedMath" ~ {
    for (f <- List(101, 303))
      s"2x$f" ~ assertEq(f + f, f * 2)
  }
}


object Example extends TestSuite {
  def assertEq[A](a: A, e: A) = ()

  test("blah blah blah") {
    test("eg 1") {
      assertEq(1 + 1, 2)
    }
    test("eg 2") {
      assertEq(1 + 1, 2)
    }
  }

  skip -> "quick" ~ assertEq(1 + 1, 2)

  "math" ~ {
    for (f <- List(1, 2, 6, 7, 8))
      s"2x$f" ~ assertEq(f + f, f * 2)
  }

  lazy val h = TestGlobal.Resource.successful("horse", 123)
  lazy val c = TestGlobal.Resource("cow", Left(OptionN.empty))

  h { i => s"horse = $i" ~ assertEq(i, 123) }
  c { i => s"cow = $i" ~ assertEq(i, 123) }

  addTest(SharedTests.blaBla)
  addTest(SharedTests.math)
}
