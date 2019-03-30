package com.gemini.jobcoin

import com.typesafe.config.ConfigFactory

import play.api.ApplicationLoader
import play.api.Configuration
import play.api.Environment
import play.api.Logger
import play.api.inject.guice.GuiceApplicationBuilder
import play.api.inject.guice.GuiceApplicationLoader

class JobcoinApplicationLoader extends GuiceApplicationLoader() {

  override def builder(context: ApplicationLoader.Context): GuiceApplicationBuilder =
    initialBuilder
      .in(context.environment)
      .loadConfig(loadFireglassConfig _)

  // When instantiating Guice, we also pass an instance of AkkaConfig
  // that contains the application configuration.
  def loadFireglassConfig(environment: Environment): Configuration = {
    val properties = System.getProperties
    val environmentName = properties.getProperty("env", "dev").toLowerCase

    Logger.info(s"Loading configuration for the `$environmentName` environment")

    // By default, Akka Config merges all the reference.conf files it can
    // find in the dependencies and the resource folders. Then, it tries to
    // load application.conf if it exists and applies it on top of the
    // reference.conf merge.
    val baseConfig = ConfigFactory.load()

    // In our business logic, we want 1) application.conf to exist,
    // and 2), apply a third configuration file on top of application.conf
    // and all the reference.conf. That third configuration file is
    // named app-ENV.conf where ENV is the environment.
    val applicationConfig = ConfigFactory
      .parseString(
        s"""
          | include "application.conf"
          | include "app-$environmentName.conf"
        """.stripMargin
      )

    // We make things to happen by merging all the configuration files
    // in the order reference.conf then application.conf then app-ENV.conf.
    Configuration(applicationConfig.withFallback(baseConfig).resolve())
  }

}
