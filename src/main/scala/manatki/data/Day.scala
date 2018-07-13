package manatki.data

import cats.syntax.coflatMap._
import cats.syntax.comonad._
import cats.syntax.semigroupal._
import cats._

trait Day[F[_], G[_], A] {
  type X
  type Y
  def fx: F[X]
  def gy: G[Y]
  def comb: (X, Y) => A
  def mapKFirst[H[_]](fk: F ~> H): Day[H, G, A]
  def mapKSecond[H[_]](fk: G ~> H): Day[F, H, A]
}

object Day extends DayInstances1 {
  def apply[F[_], G[_], X, Y, A](fx: F[X], gy: G[Y])(comb: (X, Y) => A): Day[F, G, A] = Impl(fx, gy, comb)

  private case class Impl[F[_], G[_], XX, YY, A](fx: F[XX], gy: G[YY], comb: (XX, YY) => A) extends Day[F, G, A] {
    type X = XX
    type Y = YY
    override def mapKFirst[H[_]](fk: F ~> H): Day[H, G, A]  = Impl(fk(fx), gy, comb)
    override def mapKSecond[H[_]](fk: G ~> H): Day[F, H, A] = Impl(fx, fk(gy), comb)
  }

  class DayFunctor[F[_], G[_]] extends Functor[Day[F, G, ?]] {
    override def map[A, B](fa: Day[F, G, A])(f: A => B): Day[F, G, B] =
      Impl[F, G, fa.X, fa.Y, B](fa.fx, fa.gy, (x, y) => f(fa.comb(x, y)))
  }

  class DayApply[F[_]: Semigroupal, G[_]: Semigroupal] extends DayFunctor[F, G] with Apply[Day[F, G, ?]] {
    override def ap[A, B](ff: Day[F, G, A => B])(fa: Day[F, G, A]): Day[F, G, B] = map2(ff, fa)(_(_))
    override def map2[A, B, Z](fa: Day[F, G, A], fb: Day[F, G, B])(f: (A, B) => Z): Day[F, G, Z] =
      Impl[F, G, (fa.X, fb.X), (fa.Y, fb.Y), Z](fa.fx.product(fb.fx), fa.gy.product(fb.gy), {
        case ((ax, bx), (ay, by)) => f(fa.comb(ax, ay), fb.comb(bx, by))
      })
  }

  class DayApplicative[F[_]: InvariantMonoidal, G[_]: InvariantMonoidal] extends DayApply[F, G] with Applicative[Day[F, G, ?]] {
    override val unit: Day[F, G, Unit] =
      Impl[F, G, Unit, Unit, Unit](InvariantMonoidal[F].unit, InvariantMonoidal[G].unit, (_, _) => ())
    override def pure[A](x: A): Day[F, G, A] = map(unit)(_ => x)
  }

  class DayCoflatMap[F[_]: CoflatMap, G[_]: CoflatMap] extends DayFunctor[F, G] with CoflatMap[Day[F, G, ?]] {
    override def coflatMap[A, B](fa: Day[F, G, A])(f: Day[F, G, A] => B): Day[F, G, B] =
      Impl[F, G, F[fa.X], G[fa.Y], B](fa.fx.coflatten, fa.gy.coflatten, (fx, gy) => f(Impl(fx, gy, fa.comb)))
  }

  class DayComonad[F[_]: Comonad, G[_]: Comonad] extends DayCoflatMap[F, G] with Comonad[Day[F, G, ?]] {
    override def extract[A](x: Day[F, G, A]): A = x.comb(x.fx.extract, x.gy.extract)
  }
}

sealed trait DayInstances1 extends DayInstances2 { self: Day.type =>
  implicit def applyInstance[F[_]: Semigroupal, G[_]: Semigroupal, A]: Apply[Day[F, G, ?]]                 = new DayApply
  implicit def applicative[F[_]: InvariantMonoidal, G[_]: InvariantMonoidal, A]: Applicative[Day[F, G, ?]] = new DayApplicative
}

sealed trait DayInstances2 { self: Day.type =>
  implicit def functor[F[_], G[_], A]: Functor[Day[F, G, ?]]                           = new DayFunctor
  implicit def coflatMap[F[_]: CoflatMap, G[_]: CoflatMap, A]: CoflatMap[Day[F, G, ?]] = new DayCoflatMap
  implicit def comonad[F[_]: Comonad, G[_]: Comonad, A]: CoflatMap[Day[F, G, ?]]       = new DayComonad
}