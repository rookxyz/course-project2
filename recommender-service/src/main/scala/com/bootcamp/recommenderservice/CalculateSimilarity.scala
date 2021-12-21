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

  object CalculateCosineSimilarity extends Similarity[Long] {
    override def apply(x: Array[Long], y: Array[Long]): Either[Throwable, Float] =
      if ((x.length == y.length) && (!x.isEmpty)) Right {
        val xx = normalize(x)
        val yy = normalize(y)
        dotProduct[Float](xx, yy) / (magnitude[Float](xx) * magnitude[Float](yy))
      }
      else Left(new Throwable("Array size must be equal and greater than zero"))
  }

}
