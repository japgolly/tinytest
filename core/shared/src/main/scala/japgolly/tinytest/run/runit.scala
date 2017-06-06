package japgolly.tinytest //.run

import org.scalajs.testinterface.TestUtils
import sbt.testing.SubclassFingerprint
import sbt.{testing => T}
import scala.annotation.tailrec
//import japgolly.tinytest.defn._

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

    var whitelistFound = false

    def go(groupsInnerToOuter: List[String], tt: TestTree, runNormalNow: Boolean): Unit = {
      @tailrec def go2(groupsInnerToOuter: List[String], tt: TestTree, runNormalNow: Boolean): Unit =
        tt match {

          case t: Test =>
            def path = TestPath(fqn, groupsInnerToOuter, t.name)
            t.bucket match {
              case Bucket.Normal => ex.add(path, t.body, runNormalNow)
              case Bucket.Only => ex.add(path, t.body, true); whitelistFound = true
              case Bucket.Skip(_) => ex.skip(path)
            }

          case t: Tests =>
            t.tests.foreach(go(groupsInnerToOuter, _, runNormalNow))

          case t: TestGroup =>
            t.bucket match {
              case Bucket.Normal => go2(t.name :: groupsInnerToOuter, t.testTree, runNormalNow)
              case Bucket.Only => whitelistFound = true; go2(t.name :: groupsInnerToOuter, t.testTree, true)
              case Bucket.Skip(_) => ex.skip(TestPath(fqn, groupsInnerToOuter, t.name))
            }
        }

      go2(groupsInnerToOuter, tt, runNormalNow)
    }

    suite._tests.foreach { tt =>
      go(Nil, tt, false)
    }
    ex.runScheduled(whitelistFound)

    Array.empty
  }
  override def tags(): Array[String] = Array.empty
}

case class TestPath(fqn: String, groupsInnerToOuter: List[String], name: String) {
  def fullPath: String =
    (fqn :: groupsInnerToOuter.reverse).mkString("", ".", "." + name)
}

trait TestExecutor {
  def runScheduled(skip: Boolean): Unit

  def add(p: => TestPath, t: Proc[_], runNow: Boolean): Unit

  def skip(p: => TestPath): Unit
}
object TestExecutor {
  def simple: TestExecutor =
    new TestExecutor {
      val scheduled = new collection.mutable.ArrayBuffer[(() => TestPath, Proc[_])]
      override def runScheduled(skip: Boolean): Unit =
        if (skip)
          scheduled.foreach(x => this.skip(x._1()))
        else
          scheduled.foreach(x => this.run(x._1(), x._2))

      override def add(p: => TestPath, t: Proc[_], runNow: Boolean): Unit =
        if (runNow)
          run(p, t)
        else
          scheduled += ((() => p, t))
      override def skip(p: => TestPath): Unit =
        println("Skipped: " + p.fullPath)

      def run(p: => TestPath, t: Proc[_]): Unit =
        t match {
          case f: Proc.Sync[_] =>
            f.attempt.run() // TODO Optimise
            ()
            println("Ran: " + p.fullPath)
        }
    }
}