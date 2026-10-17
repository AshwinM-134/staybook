package com.staybook.payment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import com.staybook.StaybookApplication;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.NestedExceptionUtils;

class WebhookSecretTest {

  private static final String VARIABLE = "STAYBOOK_PAYMENT_WEBHOOK_SECRET";

  @Test
  void devAndTestFallBackToTheTestSecret() {
    assumeTrue(System.getenv(VARIABLE) == null, "unset " + VARIABLE + " to run this test");
    for (String profile : List.of("dev", "test")) {
      try (ConfigurableApplicationContext context = start(profile)) {
        assertEquals("test-webhook-secret", context.getBean(WebhookSecret.class).webhookSecret());
      }
    }
  }

  @Test
  void otherProfilesDoNotStartWithoutTheVariable() {
    assumeTrue(System.getenv(VARIABLE) == null, "unset " + VARIABLE + " to run this test");
    assertDoesNotStart("prod");
  }

  @Test
  void otherProfilesDoNotStartWhenTheVariableIsBlank() {
    assertDoesNotStart("prod", "--" + VARIABLE + "= ");
  }

  @Test
  void otherProfilesReadTheVariable() {
    try (ConfigurableApplicationContext context =
        start("prod", "--" + VARIABLE + "=from-the-environment")) {
      assertEquals("from-the-environment", context.getBean(WebhookSecret.class).webhookSecret());
    }
  }

  @Test
  void toStringHidesTheSecret() {
    assertFalse(
        new WebhookSecret("from-the-environment").toString().contains("from-the-environment"));
  }

  private static ConfigurableApplicationContext start(String profile, String... args) {
    SpringApplication app = new SpringApplication(PropertiesScan.class);
    app.setWebApplicationType(WebApplicationType.NONE);
    app.setAdditionalProfiles(profile);
    return app.run(args);
  }

  private static void assertDoesNotStart(String profile, String... args) {
    Exception failure = assertThrows(Exception.class, () -> start(profile, args).close());
    String reason = NestedExceptionUtils.getMostSpecificCause(failure).getMessage();
    assertTrue(reason.contains(VARIABLE + " is required"), reason);
  }

  @Configuration(proxyBeanMethods = false)
  @ConfigurationPropertiesScan(basePackageClasses = StaybookApplication.class)
  static class PropertiesScan {}
}
