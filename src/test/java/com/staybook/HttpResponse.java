package com.staybook;

final class HttpResponse {

  private final int statusCode;
  private final byte[] payload;

  HttpResponse(int code, byte[] responseBody) {
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
