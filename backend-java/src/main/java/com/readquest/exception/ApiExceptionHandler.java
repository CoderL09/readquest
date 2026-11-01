package com.readquest.exception;

import com.readquest.vo.ApiError;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice
public class ApiExceptionHandler {
  @ExceptionHandler(ApiException.class)
  ResponseEntity<?> api(ApiException e) {
    return ResponseEntity.status(e.status()).body(ApiError.of(e.code(), e.getMessage()));
  }

  @ExceptionHandler({
    MethodArgumentNotValidException.class,
    ConstraintViolationException.class,
    HttpMessageNotReadableException.class,
    org.springframework.web.multipart.support.MissingServletRequestPartException.class,
    org.springframework.web.bind.MissingServletRequestParameterException.class,
    org.springframework.web.method.annotation.HandlerMethodValidationException.class,
    org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class
  })
  ResponseEntity<?> invalid(Exception e) {
    var violations =
        e instanceof MethodArgumentNotValidException invalid
            ? invalid.getBindingResult().getFieldErrors().stream()
                .map(field ->
                    new ApiError.Violation(
                        field.getField(),
                        field.getDefaultMessage() == null
                            ? "Invalid value"
                            : field.getDefaultMessage()))
                .distinct()
                .toList()
            : java.util.List.<ApiError.Violation>of();
    return ResponseEntity.unprocessableEntity()
        .body(ApiError.of("VALIDATION_ERROR", "Invalid request", violations));
  }

  @ExceptionHandler(DuplicateKeyException.class)
  ResponseEntity<?> duplicate() {
    return error(409, "Resource already exists");
  }

  @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
  ResponseEntity<?> tooLarge() {
    return error(413, "File exceeds upload size limit");
  }

  @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
  ResponseEntity<?> methodNotAllowed(
      org.springframework.web.HttpRequestMethodNotSupportedException error) {
    var response = ResponseEntity.status(405);
    if (error.getSupportedHttpMethods() != null)
      response.allow(
          error.getSupportedHttpMethods().toArray(org.springframework.http.HttpMethod[]::new));
    return response.body(ApiError.of("HTTP_405", "Method not allowed"));
  }

  @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
  ResponseEntity<?> unsupportedMedia() {
    return error(415, "Unsupported media type");
  }

  @ExceptionHandler({
    org.springframework.web.servlet.resource.NoResourceFoundException.class,
    org.springframework.web.servlet.NoHandlerFoundException.class
  })
  ResponseEntity<?> missing() {
    return error(404, "Resource not found");
  }

  @ExceptionHandler(org.springframework.security.access.AccessDeniedException.class)
  ResponseEntity<?> denied() {
    return ResponseEntity.status(403).body(ApiError.of("FORBIDDEN", "Insufficient permission"));
  }

  @ExceptionHandler(Exception.class)
  ResponseEntity<?> unexpected(Exception error) {
    LoggerFactory.getLogger(getClass()).error("Unhandled request failure", error);
    return ResponseEntity.internalServerError()
        .body(ApiError.of("INTERNAL_ERROR", "Unexpected server error"));
  }

  private ResponseEntity<?> error(int status, String message) {
    return ResponseEntity.status(status).body(ApiError.of("HTTP_" + status, message));
  }
}
