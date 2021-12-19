package com.bootcamp.recommenderservice
import Numeric.Implicits._

trait Similarity[T] {
  def apply(x: Array[T], y: Array[T]): Float
}
object CalculateSimilarity {

  def magnitude[T: Numeric](x: Array[T]): Float =
    math.sqrt(x map (i => i.toFloat() * i.toFloat()) sum).toFloat

  def dotProduct[T: Numeric](x: Array[T], y: Array[T]): T =
    (for ((a, b) <- x zip y) yield a * b) sum

  def normalize[T: Numeric](x: Array[T]): Array[Float] = x.map(i => i.toFloat() / x.max.toFloat())

  object CalculateCosineSimilarity extends Similarity[Long] {
    override def apply(x: Array[Long], y: Array[Long]): Float = {
      require(x.length == y.length)
      dotProduct[Long](x, y) / (magnitude[Long](x) * magnitude[Long](y))
    }
  }

}
