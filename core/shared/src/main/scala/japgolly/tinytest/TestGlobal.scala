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

  case class Resource[+A](name: String, value: OptionN[String] \/ (A, OnShutdown)) {
    value.foreach(x => TestGlobal.onShutdownProc(x._2))

    def apply[B](body: A => B)(implicit t: TestDsl): Option[B] =
      value match {
        case Right((a, _)) => Some(body(a))
        case Left(e) => t.skip(e getOrElse s"$name unavailable")(t.group(s"$name-based test(s)")(None))
      }

  }
}
