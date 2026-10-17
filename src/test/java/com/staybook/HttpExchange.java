package com.staybook;

/** Status and body from one HTTP call. */
final class HttpExchange {

  private final int statusCode;
  private final byte[] payload;

  HttpExchange(int code, byte[] responseBody) {
    statusCode = code;
    payload = responseBody == null ? new byte[0] : responseBody.clone();
  }

  int status() {
    return statusCode;
  }

  byte[] body() {
    return payload.clone();
  }
}
