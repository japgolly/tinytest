package japgolly.tinytest

final case class OptionN[+A >: Null](get: A) extends AnyVal {
  @inline def isEmpty: Boolean = get == null
  @inline def nonEmpty: Boolean = get != null

  def getOrElse[B >: A](fallback: => B): B =
    if (get == null) fallback else get

  def map[B >: Null](f: A => B): OptionN[B] =
    OptionN(if (get == null) null else f(get))

  def flatMap[B >: Null](f: A => OptionN[B]): OptionN[B] =
    if (get == null) OptionN(null) else f(get)

  def fold[B](fallback: => B, f: A => B): B =
    if (get == null) fallback else f(get)

  def exists(f: A => Boolean): Boolean =
    nonEmpty && f(get)

  def forall(f: A => Boolean): Boolean =
    isEmpty || f(get)

  def filter(f: A => Boolean): OptionN[A] =
    if (isEmpty || !f(get)) OptionN(null) else this
}

object OptionN {
  @inline def empty[A >: Null]: OptionN[A] =
    apply(null)

  def when[A >: Null](cond: Boolean, a: => A): OptionN[A] =
    apply(if (cond) a else null)
}