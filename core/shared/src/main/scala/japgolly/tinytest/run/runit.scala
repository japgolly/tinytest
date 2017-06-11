package japgolly.tinytest //.run

import org.scalajs.testinterface.TestUtils
import sbt.testing.SubclassFingerprint
import sbt.{testing => T}
import scala.annotation.tailrec

object TinyTest {
  val fingerprint: T.Fingerprint =
    new SubclassFingerprint {
      override def isModule = true
      override def requireNoArgConstructor() = true
      override def superclassName() = "japgolly.tinytest.TestSuite"
    }
}

final class Framework extends T.Framework {
  override def name(): String = "TinyTest"

  override def fingerprints(): Array[T.Fingerprint] = Array(TinyTest.fingerprint)

  override def runner(args: Array[String], remoteArgs: Array[String], cl: ClassLoader): T.Runner =
    Runner(args, remoteArgs, cl)
}

final case class Runner(args: Array[String], remoteArgs: Array[String], cl: ClassLoader) extends T.Runner {

  println("Starting...")

  scala.concurrent.ExecutionContext.global


  val ex = TestExecutor.simple

  override def tasks(taskDefs: Array[T.TaskDef]): Array[T.Task] = {
    taskDefs.map(Task(_, cl, ex))
  }

  override def done(): String = {
//    Thread.sleep(4000)
    "Go fuck yourself"
  }
}

final case class Task(taskDef: T.TaskDef, cl: ClassLoader, ex: TestExecutor) extends T.Task {
//  override def taskDef() = td
  override def execute(eventHandler: T.EventHandler, loggers: Array[T.Logger]): Array[T.Task] = {
    val fqn = taskDef.fullyQualifiedName()
    val suite = TestUtils.loadModule(fqn, cl).asInstanceOf[TestSuite]

//- runTests: (T, path)
//  - aroundAll
//    - determine tests to run, skip others
//    - foreach test
//      - aroundEach
//        - run test
//        - if it added more, run those too (T₂, path+)

    def runTests(tests: Tests, pathInnerToOuter: List[String]): Unit = {
      tests.aroundAll {

        // determine tests to run, skip others
        val normals = List.newBuilder[(Test, TestPath)]
        val onlys = List.newBuilder[(Test, TestPath)]
        tests.children foreach {
          case t: Test =>
            val path = TestPath(fqn, pathInnerToOuter, t.name)
            def skip(s: Bucket.Skip) = ex.skip(path, s.reason)

            t.bucket match {
              case Bucket.Normal => normals += ((t, path))
              case Bucket.Only => onlys += ((t, path))
              case b: Bucket.Skip => skip(b)
              case Bucket.When(sf) =>
                val s = sf()
                if (s.isEmpty)
                  skip(s.get)
                else
                  normals += ((t, path))
            }
          //        case ts: Tests =>
        }
        val os = onlys.result()
        val ns = normals.result()
        val testsToRun: List[(Test, TestPath)] =
          if (os.isEmpty)
            ns
          else {
            ns.foreach(n => ex.skip(n._2, OptionN.empty))
            os
          }
        
        for ((test, path) <- testsToRun) {
          tests.aroundEach {
            val ctx2 = new Ctx
            suite._withCtx(ctx2) { // TODO Hey, won't work for shared tests, curse mutability and all its benefits
              ex.runSync(path, test.body)
            }
            if (ctx2.tests.children.nonEmpty)
              runTests(ctx2.tests.result(), test.name :: pathInnerToOuter)
          }
        }
      }
    }
    
    runTests(suite._ctx.tests.result(), Nil)

    Array.empty
  }
  override def tags(): Array[String] = Array.empty
}

case class TestPath(fqn: String, pathInnerToOuter: List[String], name: String) {
  def fullPath: String =
    (fqn :: pathInnerToOuter.reverse).mkString("", ".", "." + name)
}

trait TestExecutor {
  def runSync(path: => TestPath, test: Proc[_]): Unit
  def skip(path: => TestPath, reason: OptionN[String]): Unit
}
object TestExecutor {
  def simple: TestExecutor =
    new TestExecutor {
      import Console._

      override def skip(path: => TestPath, reason: OptionN[String]): Unit = {
        var s = s"[${CYAN}skip$RESET] ${path.fullPath}"
        if (reason.nonEmpty)
          s+=s" - ${CYAN}${reason.get}"
        s += RESET
        println(s)
      }

      def runSync(path: => TestPath, test: Proc[_]): Unit =
        test match {
          case f: Proc.Sync[_] =>
            val dur = Duration.timeSync(
              f.attempt.run() // TODO Optimise
            )
            println(s"[${GREEN}pass$RESET] ${path.fullPath} $BOLD$BLACK(${dur.fmt})$RESET")
        }
    }
}

final case class Duration(nanos: Long) extends AnyVal {
  def fmt = "%,d μs".format(nanos / 1000)
}
object Duration {
  def timeSync(f: => Unit): Duration = {
    val start = System.nanoTime()
    f
    val end = System.nanoTime()
    new Duration(end - start)
  }
}