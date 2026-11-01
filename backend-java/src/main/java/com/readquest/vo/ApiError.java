package com.readquest.vo;

import java.util.List;
import org.slf4j.MDC;

public record ApiError(Detail error) {
  public record Violation(String field, String message) {}

  public record Detail(String code, String message, String requestId, List<Violation> details) {}

  public static ApiError of(String code, String message) {
    return of(code, message, List.of());
  }

  public static ApiError of(String code, String message, List<Violation> details) {
    return new ApiError(new Detail(code, message, MDC.get("requestId"), details));
  }
}
