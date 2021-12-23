package com.bootcamp.recommenderservice
import Numeric.Implicits._

trait Similarity[T] {
  def apply(x: Vector[T], y: Vector[T]): Either[Throwable, Float]
}
object CalculateSimilarity {

  def magnitude[T: Numeric](x: Vector[T]): Float =
    math
      .sqrt(
        x
          .map(i => i.toFloat() * i.toFloat())
          .sum,
      )
      .toFloat

  def dotProduct[T: Numeric](x: Vector[T], y: Vector[T]): T =
    (for ((a, b) <- x zip y) yield a * b).sum

  def normalize[T: Numeric](x: Vector[T]): Vector[Float] = {
    val xMax = x.max.toFloat()
    x.map(i => i.toFloat() / xMax)
  }

  object CalculateCosineSimilarity extends Similarity[Float] {
    def apply(x: Vector[Float], y: Vector[Float]): Either[Throwable, Float] =
      if ((x.length == y.length) && x.nonEmpty) Right {
        dotProduct[Float](x, y) / (magnitude[Float](x) * magnitude[Float](y))
      }
      else Left(new Throwable("Vector size must be equal and greater than zero"))
  }

}
