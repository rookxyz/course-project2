package com.bootcamp.playerrepository

import java.io.ByteArrayOutputStream
import java.util.zip.GZIPOutputStream
import scala.io.Source

object CompressString {
  def compress(str: String): Array[Byte] = {
    val baos = new ByteArrayOutputStream()
    val gzipOut = new GZIPOutputStream(baos)
    gzipOut.write(str.getBytes("UTF-8"))
    gzipOut.close()
    baos.toByteArray
  }

  def unCompress(compressed: Array[Byte]): String = {
    import java.io.ByteArrayInputStream
    import java.util.zip.GZIPInputStream
    val bis = new ByteArrayInputStream(compressed)
    val gis = new GZIPInputStream(bis)
    val res = Source.fromInputStream(gis, "UTF-8").getLines.take(1).toList.head
    gis.close
    res
  }
}
