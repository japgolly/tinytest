package tinytest.assertion.test

import org.junit.Test
import org.junit.Assert._

class Test1 {

  @Test def testC(): Unit = {
    import tinytest.assertion.AsClass.SourceInfo
    val i = the[SourceInfo]
    assertEquals(10, i.lineNo)
  }

  @Test def testO(): Unit = {
    import tinytest.assertion.AsOpaqueType.SourceInfo

    // import delegate tinytest.assertion.AsOpaqueType.SourceInfo._
    // val i = the[SourceInfo]

    val i = SourceInfo.bug
    assertEquals(20, i.lineNo)
  }
}
