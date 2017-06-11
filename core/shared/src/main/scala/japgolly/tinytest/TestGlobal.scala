package japgolly.tinytest

import scala.concurrent.Future
import scala.{Either => \/}

object TestGlobal {

  type OnShutdown = Proc[Unit]

  private val lock = new AnyRef
  private def mutex[A](a: => A): A = lock.synchronized(a)

  private var _onShutdown = List.empty[OnShutdown]

  private def onShutdownProc(proc: Proc[Unit]): Unit =
    mutex(_onShutdown ::= proc)

  def onShutdown(run: () => Unit): Unit =
    onShutdownProc(Proc.Sync(run))

  def onShutdownAsync(run: () => Future[Unit]): Unit =
    onShutdownProc(Proc.Async(run))

  /*
  final class Resource[+A](name: String, value: OptionN[String] \/ (A, OnShutdown)) {

    // Install shutdown hook
    value.toOption.map(_._2).filter(_ ne Proc.unit).foreach(TestGlobal.onShutdownProc)

    def apply[B](body: A => B)(implicit t: TestDsl): Option[B] =
      value match {
        case Right((a, _)) => Some(body(a))
        case Left(e) => t.skip(e getOrElse s"$name unavailable")(t.group(s"$name-based test(s)")(None))
      }

    def onShutdown(f: A => Unit): Resource[A] =
      new Resource[A](name, init.map(a => (a, Proc.Sync(() => f(a)))))

    def onShutdownAsync(f: A => Future[Unit]): Resource[A] =
      new Resource[A](name, init.map(a => (a, Proc.Async(() => f(a)))))
  }

  object Resource {
    def apply[A](name: String, init: OptionN[String] \/ A): Builder[A] =
      new Builder(name, init)

    def attempt[A](name: String, a: => A, e: Throwable => OptionN[String]): Builder[A] =
      apply(name, Proc.Sync(() => a).attempt.run().left.map(e))

    def successful[A](name: String, a: A): Builder[A] =
      apply(name, Right(a))

    final class Builder[A](name: String, init: OptionN[String] \/ A) {
      def noShutdown: Resource[A] =
        new Resource[A](name, init.map((_, Proc.unit)))

      def shutdown(f: A => Unit): Resource[A] =
        new Resource[A](name, init.map(a => (a, Proc.Sync(() => f(a)))))

      def shutdownAsync(f: A => Future[Unit]): Resource[A] =
        new Resource[A](name, init.map(a => (a, Proc.Async(() => f(a)))))
    }

  }
  */

  final class Resource[+A](name: String, value: OptionN[String] \/ A) {

    def apply[B](body: A => B)(implicit t: TestDsl): Option[B] =
      value match {
        case Right((a, _)) => Some(body(a))
        case Left(e) => t.skip(e getOrElse s"$name unavailable")(t.group(s"$name-based test(s)")(None))
      }

    def onShutdown(f: A => Unit): this.type = {
      value.foreach(a => TestGlobal.onShutdown(() => f(a)))
      this
    }

    def onShutdownAsync(f: A => Future[Unit]): this.type = {
      value.foreach(a => TestGlobal.onShutdownAsync(() => f(a)))
      this
    }
  }

  object Resource {
    def apply[A](name: String, init: OptionN[String] \/ A): Resource[A] =
      new Resource(name, init)

    def attempt[A](name: String, a: => A, e: Throwable => OptionN[String]): Resource[A] =
      apply(name, Proc.Sync(() => a).attempt.run().left.map(e))

    def successful[A](name: String, a: A): Resource[A] =
      apply(name, Right(a))
  }
}
