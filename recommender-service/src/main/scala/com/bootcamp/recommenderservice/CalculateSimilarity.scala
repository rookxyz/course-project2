package com.bootcamp.recommenderservice
import Numeric.Implicits._

trait Similarity[T] {
  def apply(x: Array[T], y: Array[T]): Either[Throwable, Float]
}
object CalculateSimilarity {

  def magnitude[T: Numeric](x: Array[T]): Float =
    math
      .sqrt(
        x
          .map(i => i.toFloat() * i.toFloat())
          .sum,
      )
      .toFloat

  def dotProduct[T: Numeric](x: Array[T], y: Array[T]): T =
    (for ((a, b) <- x zip y) yield a * b).sum

  def normalize[T: Numeric](x: Array[T]): Array[Float] = {
    val xMax = x.max.toFloat()
    x.map(i => i.toFloat() / xMax)
  }

  object CalculateCosineSimilarity extends Similarity[Float] {
    def apply(x: Array[Float], y: Array[Float]): Either[Throwable, Float] =
      if ((x.length == y.length) && (!x.isEmpty)) Right {
        dotProduct[Float](x, y) / (magnitude[Float](x) * magnitude[Float](y))
      }
      else Left(new Throwable("Array size must be equal and greater than zero"))
  }

}
