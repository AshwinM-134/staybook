package com.staybook;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/** Entry point for the level 0 monolith. */
@SpringBootApplication
public class StaybookApplication {

  /** Boots the application. */
  public static void main(String[] args) {
    SpringApplication.run(StaybookApplication.class, args);
  }
}
