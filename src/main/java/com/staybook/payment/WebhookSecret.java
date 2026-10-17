package com.staybook.payment;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** HMAC key for payment webhooks. The app does not start when it is blank. */
@Validated
@ConfigurationProperties("staybook.payment")
record WebhookSecret(
    @NotBlank(message = "STAYBOOK_PAYMENT_WEBHOOK_SECRET is required") String webhookSecret) {

  @Override
  public String toString() {
    return "WebhookSecret[webhookSecret=<redacted>]";
  }
}
