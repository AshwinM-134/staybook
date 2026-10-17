package com.staybook.payment;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Resolving the webhook secret at startup stops the process when it is missing. */
@Component
final class WebhookSecret {

  private final String secret;

  WebhookSecret(@Value("${staybook.payment.webhook-secret}") String secret) {
    this.secret = secret;
  }

  @PostConstruct
  void rejectBlank() {
    if (secret.isBlank()) {
      throw new IllegalStateException("STAYBOOK_PAYMENT_WEBHOOK_SECRET is required");
    }
  }
}
