package com.staybook;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.containers.PostgreSQLContainer;

/** One database for every test class that imports it. Boot starts it and connects the app. */
final class SharedPostgres {

  @ServiceConnection
  static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

  private SharedPostgres() {}
}
