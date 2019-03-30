package com.gemini.jobcoin

import scala.concurrent.duration.DurationInt

import com.gemini.jobcoin.Implicits.RichConfiguration

import javax.inject.Inject
import javax.inject.Singleton
import play.api.Configuration

@Singleton
class JobcoinConfiguration @Inject()(config: Configuration) {

  class HttpClientInnerConfig {
    val HttpRequestTimeout = 10.seconds
    val BaseUrl = config.get[String]("jobcoin.http-client.base-url")
  }
  val HttpClient = new HttpClientInnerConfig

  class MixerInnerConfig {
    val AddressIds: List[String] = config.getListOfStrings("jobcoin.mixer.addresses")
    val MaxRebalancingAttempts = 20
    val RebalancingVariance = 0.2
    val Fee = 0.1
    val HiddenWalletsCount = config.get[Int]("jobcoin.mixer.hidden-wallets-count")
    val CheckInterval = config.getFiniteDuration("jobcoin.mixer.check-interval")
    val ExponentialBackoff = 3
    val MinimumRetryInterval = 3.seconds
    val MaximumRetryInterval = 30.seconds
  }
  val Mixer = new MixerInnerConfig

}
