package com.staybook;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** Entry point for the level 0 monolith. */
@SpringBootApplication
@ConfigurationPropertiesScan
public class StaybookApplication {

  /** Boots the application. */
  public static void main(String[] args) {
    SpringApplication.run(StaybookApplication.class, args);
  }
}
