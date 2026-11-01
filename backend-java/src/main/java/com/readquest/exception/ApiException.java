package com.readquest.exception;

public class ApiException extends RuntimeException {
  private final int status;
  private final String code;

  public ApiException(int status, String message) {
    super(message);
    this.status = status;
    this.code = "HTTP_" + status;
  }

  public ApiException(int status, String code, String message) {
    super(message);
    this.status = status;
    this.code = code;
  }

  public String code() {
    return code;
  }

  public int status() {
    return status;
  }
}
