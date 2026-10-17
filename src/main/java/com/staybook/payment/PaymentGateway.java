package com.staybook.payment;

/**
 * Boundary for the payment provider. The provider is a separate process. Call it only after the
 * database transaction that updates inventory has committed.
 */
public interface PaymentGateway {

  /**
   * Creates an order at the provider.
   *
   * @param amountMinor amount in minor currency units
   * @param currency ISO-4217 currency code
   * @param receipt caller reference, typically the booking idempotency key
   * @return provider order id
   */
  String createOrder(long amountMinor, String currency, String receipt);
}
