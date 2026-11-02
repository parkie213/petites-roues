package com.petitesroues.web;

import com.petitesroues.library.TitleExistsException;
import com.petitesroues.speech.SpeechUnavailableException;
import java.util.NoSuchElementException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;
import tools.jackson.core.JacksonException;

/** Keep the existing {error: message} API format, including Spring's request-binding errors. */
@RestControllerAdvice
public class ApiExceptionHandler extends ResponseEntityExceptionHandler {
  @ExceptionHandler(TitleExistsException.class)
  public ResponseEntity<Object> titleExists(Exception exception) {
    return error(HttpStatus.CONFLICT, exception.getMessage(), new HttpHeaders());
  }

  private static final Logger LOG = LoggerFactory.getLogger(ApiExceptionHandler.class);

  public record ApiError(String error) {}

  private ResponseEntity<Object> error(HttpStatusCode status, String message, HttpHeaders headers) {
    HttpStatus knownStatus = HttpStatus.resolve(status.value());
    String fallback = knownStatus == null ? "Request failed" : knownStatus.getReasonPhrase();
    return ResponseEntity.status(status)
        .headers(headers)
        .body(new ApiError(message == null ? fallback : message));
  }

  @Override
  protected ResponseEntity<Object> handleExceptionInternal(
      Exception exception,
      Object body,
      HttpHeaders headers,
      HttpStatusCode status,
      WebRequest request) {
    return error(status, exception.getMessage(), headers);
  }

  @ExceptionHandler({IllegalArgumentException.class, JacksonException.class})
  public ResponseEntity<Object> badRequest(Exception exception) {
    return error(HttpStatus.BAD_REQUEST, exception.getMessage(), new HttpHeaders());
  }

  @ExceptionHandler(SpeechUnavailableException.class)
  public ResponseEntity<Object> speechUnavailable(Exception exception) {
    return error(HttpStatus.SERVICE_UNAVAILABLE, exception.getMessage(), new HttpHeaders());
  }

  @ExceptionHandler(NoSuchElementException.class)
  public ResponseEntity<Object> missing(NoSuchElementException exception) {
    return error(HttpStatus.NOT_FOUND, exception.getMessage(), new HttpHeaders());
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Object> unexpected(Exception exception) {
    LOG.error("API request failed", exception);
    return error(HttpStatus.INTERNAL_SERVER_ERROR, "Request failed", new HttpHeaders());
  }
}
