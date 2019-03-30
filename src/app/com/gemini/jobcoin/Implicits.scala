package com.gemini.jobcoin

import java.math.BigDecimal.ROUND_FLOOR
import java.math.BigDecimal
import scala.collection.JavaConversions.asScalaBuffer
import scala.collection.JavaConversions.asScalaSet
import scala.concurrent.duration.Duration
import scala.concurrent.duration.FiniteDuration
import scala.language.implicitConversions
import scala.util.Failure
import scala.util.Success
import scala.util.Try

import com.typesafe.config.ConfigList

import play.api.Configuration

object Implicits {

  implicit class RichDouble(double: Double) {
    def setScale(scale: Int) = {
      val bigDecimal = new BigDecimal(double)
      bigDecimal.setScale(scale, ROUND_FLOOR).doubleValue().toDouble
    }
  }

  implicit class RichConfiguration(config: Configuration) {
    def getListOfStrings(path: String): List[String] =
      config.underlying
        .getStringList(path)
        .toList

    def getDuration(path: String): Duration = {
      val durationString = config.get[String](path)

      Try(Duration.create(durationString)) match {
        case Success(duration) => duration
        case Failure(e) =>
          throw new IllegalArgumentException(
            s"Duration could not be parsed at $path: $durationString",
            e
          )
      }
    }

    def getFiniteDuration(path: String): FiniteDuration = {
      val duration = getDuration(path)
      if (duration.isFinite) {
        FiniteDuration(duration.length, duration.unit)
      } else {
        throw new IllegalArgumentException(
          s"Invalid duration at $path: $duration. It must be finite"
        )
      }
    }
  }

}
