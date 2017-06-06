package japgolly.tinytest

import scala.concurrent.{ExecutionContext, Future}
import scala.{Either => \/}

/*
[*] Before/after each (in some scope)
[*] Before/after all (in some scope)
[*] Lazy global resources (init when needed, shutdown at end of ALL tests)
[*] Sole/ignore
[*] Dynamic/data-based tests (eg. ∀a∈[…:A]. name x test)
[*] Shared tests (test tree as data)
[ ] Async tests
[*] Nested tests / groups
[ ] Dynamically skip tests when `<runtime reason>` (eg. no internet → report "18 tests skipped because no internet")
[ ] Support testing compilation errors



Should just be one type of usage/DSL.
But for tests, extend TestSuite
  - .tests ArrayBuffer hidden
For shared extend something else
  - .tests returns an immutable TestTree

group  - modifies thread-local ctx so that wraps test(){}
test   - create, add to ctx, return Test
sole{} - modifies thread-local ctx

group(name) {
  beforeEach {}
  test(name){}
  test(name){}
  ignore -> test(name){}
  ignore{ test(name){} }
}

*/


sealed abstract class Bucket
object Bucket {
  case object Normal extends Bucket
  case object Only extends Bucket
  case class Skip(reason: OptionN[String]) extends Bucket
  case class When(skip: () => OptionN[Skip]) extends Bucket
}

sealed abstract class Proc[A] {
  def fold[B](s: Proc.Sync[A] => B, a: Proc.Async[A] => B): B
  def async(implicit ec: ExecutionContext): Proc.Async[A]
//  def map[B](f: A => B): Proc[B]
//  def flatMap[B](f: A => Proc[B]): Proc[B]
}
object Proc {
  final case class Sync[A](run: () => A) extends Proc[A]  {
    override def fold[B](s: Proc.Sync[A] => B, a: Proc.Async[A] => B): B = s(this)
    override def async(implicit ec: ExecutionContext) = Async(() => Future(run()))
    def map[B](f: A => B): Sync[B] = Sync(() => f(run()))
//    def flatMap[B](f: A => Proc[B]): Async[B] = Async(() => f(run()).async.run())
    def attempt: Sync[Throwable \/ A] = Sync(() => try Right(run()) catch {case t: Throwable => Left(t)})
  }
  final case class Async[A](run: () => Future[A]) extends Proc[A] {
    override def fold[B](s: Proc.Sync[A] => B, a: Proc.Async[A] => B): B = a(this)
    override def async(implicit ec: ExecutionContext) = this
    def map[B](f: A => B)(implicit ec: ExecutionContext): Async[B] = Async(() => run().map(f))
//    def flatMap[B](f: A => Proc[B])(implicit ec: ExecutionContext): Async[B] = Async(() => run().flatMap(f(_).async.run()))
    def attempt(implicit ec: ExecutionContext): Async[Throwable \/ A] =
      Async(() =>
        try
          run().transformWith(t => Future.successful(t.toEither))
        catch {
          case t: Throwable => Future.successful(Left(t))
        })
  }

  val unit = Sync(() => ())
}

// TODO Prefix all the things with Tiny?

sealed trait TestTree {

  def prepend(t: TestTree): Tests =
    Tests(t :: this :: Nil)

  @inline final def append(t: TestTree): Tests =
    t.prepend(this)
}
object TestTree {
  val fromList: List[TestTree] => TestTree = {
    case t :: Nil => t
    case ts => Tests(ts)
  }
}

final case class Test(name: String, body: Proc[_], bucket: Bucket) extends TestTree

final case class TestGroup(name: String, testTree: TestTree, bucket: Bucket,
                           aroundAll: Around, aroundEach: Around) extends TestTree

final case class Tests(tests: List[TestTree]) extends TestTree {
  override def prepend(t: TestTree): Tests =
    Tests(t :: tests)
}

final case class Around(run: (() => Unit) => Unit) extends AnyVal {
  def isEmpty: Boolean =
    run eq Around.empty.run

  def insideOf(outer: Around): Around =
    if (isEmpty) outer
    else if (outer.isEmpty) this
    else Around(test => outer.run(() => run(test)))
}

object Around {
  val empty: Around =
    Around(_())
  
  def before(f: () => Unit): Around = Around(test => { f(); test() })
  def after(f: () => Unit): Around = Around(test => { test(); f() })

  final class Var {
    var get = Around.empty
    def addInside(i: Around): Unit =
      get = i insideOf get
  }
}

object TestDsl {
  final class StringExt(private val name: String) extends AnyVal {
    @inline def ~(body: => Any)(implicit t: TestDsl): Test =
      t.test(name)(body)
  }

  trait WithBucket {
    def apply[A](a: => A): A
    @inline final def ->[A](a: => A): A = apply(a)
  }

//  type Around = (() => Unit) => Unit
}

final case class Ctx(add: TestTree => Unit,
                     aroundAll: Around.Var = new Around.Var,
                     aroundEach: Around.Var = new Around.Var,
                     bucket: Bucket = Bucket.Normal)

sealed trait TestDsl {
  import TestDsl._
  
  protected implicit final def _tinytestStringExt(a: String): StringExt = new StringExt(a)
  protected implicit final def _tinytestTestDsl: TestDsl = this

  private[tinytest] val _tests = new collection.mutable.ArrayBuffer[TestTree]
  private[tinytest] var _ctx = Ctx(_tests += _)

  private def withCtx[A](c: Ctx)(a: => A): A = {
    val o = _ctx
    try {_ctx = c; a} finally _ctx = o
  }

  def addTest(t: TestTree): t.type = {
    _ctx.add(t)
    t
  }
  def addTests(ts: TestTree*): Unit =
    ts.foreach(addTest(_)) // not [ts foreach _add] cos addTest may be overridden

  def test(name: String)(body: => Any): Test =
    addTest(Test(name, Proc.Sync(() => body), _ctx.bucket))

//  def testAsync(name: String)(body: => Future[Any]): Test =
//    addTest(Test(name, Proc.Async(() => body), _ctx.bucket))

  def beforeAll(run: () => Unit): Unit = aroundAll(Around.before(run).run)
  def  afterAll(run: () => Unit): Unit = aroundAll(Around.after(run).run)
  def aroundAll(run: (() => Unit) => Unit): Unit = _ctx.aroundAll.addInside(Around(run))

  def beforeEach(run: () => Unit): Unit = aroundEach(Around.before(run).run)
  def  afterEach(run: () => Unit): Unit = aroundEach(Around.after(run).run)
  def aroundEach(run: (() => Unit) => Unit): Unit = _ctx.aroundEach.addInside(Around(run))

  private def withBucket(b: Bucket): WithBucket =
    new WithBucket {
      override def apply[A](a: => A) =
        withCtx(_ctx.copy(bucket = b))(a)
    }

  final def only: WithBucket = withBucket(Bucket.Only)
  final def skip: WithBucket = withBucket(Bucket.Skip(OptionN.empty))
  final def skip(reason: String): WithBucket = withBucket(Bucket.Skip(OptionN(reason)))
  final def when(cond: => Boolean): WithBucket = _when(cond, Bucket.Skip(OptionN.empty))
  final def when(cond: => Boolean, reason: => String): WithBucket = _when(cond, Bucket.Skip(OptionN(reason)))
  final def unless(cond: => Boolean): WithBucket = when(!cond)
  final def unless(cond: => Boolean, reason: => String): WithBucket = when(!cond, reason)
  final private def _when(cond: => Boolean, skip: => Bucket.Skip): WithBucket = withBucket(Bucket.When(() => OptionN.when(cond, skip)))

  def group[A](name: String)(body: => A): A = {
    val groupTests = List.newBuilder[TestTree]
    val ctx = Ctx(groupTests += _, bucket = _ctx.bucket)
    val result = withCtx(ctx)(body)
    val testTree = TestTree.fromList(groupTests.result())
    val testGroup = TestGroup(name, testTree, ctx.bucket, ctx.aroundAll.get, ctx.aroundEach.get)
    _ctx.add(testGroup)
    result
  }

}

trait TestSuite extends TestDsl

trait TestAuthor extends TestDsl {
  def tests: TestTree =
    TestTree.fromList(_tests.toList)
}
