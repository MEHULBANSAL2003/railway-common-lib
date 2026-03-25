package com.railway.common.exception;

import com.railway.common.dto.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.security.access.AccessDeniedException;

/**
 * ONE class that handles ALL exceptions across the entire service.
 *
 * HOW IT WORKS:
 *   When any controller throws an exception (or an exception bubbles
 *   up from service/repository layer), Spring looks here FIRST before
 *   sending a response. It finds the matching @ExceptionHandler method
 *   and uses THAT response instead of the default ugly error page.
 *
 * WHY @RestControllerAdvice (not @ControllerAdvice)?
 *   @RestControllerAdvice = @ControllerAdvice + @ResponseBody
 *   Means: return values are automatically serialized to JSON.
 *   We don't need @ResponseBody on each method.
 *
 * WHY in common-lib?
 *   Every service needs this exact same error handling.
 *   Without it, each service would need its own handler = redundancy.
 *   One handler, shared by all services.
 *
 * PRIORITY ORDER:
 *   Spring picks the MOST SPECIFIC handler first.
 *   If a BadRequestException is thrown:
 *     1. Checks: is there a handler for BadRequestException? No.
 *     2. Checks: is there a handler for BaseException (parent)? YES → uses it.
 *   If a MethodArgumentNotValidException is thrown:
 *     1. Checks: is there a handler for it? YES → uses it directly.
 *
 * LOGGING STRATEGY:
 *   - 4xx errors (client mistakes) → log.warn (client's fault, not critical)
 *   - 5xx errors (our mistakes)    → log.error (we need to investigate)
 *   - This distinction matters in production monitoring. You alert on
 *     error-level logs, not warn-level. Otherwise every invalid login
 *     attempt would page your on-call engineer at 3 AM.
 */
@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

  /**
   * Handles ALL our custom exceptions (BaseException subclasses).
   *
   * Since BadRequestException, ResourceNotFoundException,
   * UnauthorizedException, etc. all extend BaseException,
   * this ONE method catches them all.
   *
   * The HTTP status code comes from the exception itself
   * (set in each subclass constructor). So:
   *   BadRequestException      → 400
   *   ResourceNotFoundException → 404
   *   UnauthorizedException    → 401
   *   ForbiddenException       → 403
   *   ConflictException        → 409
   *   TooManyRequestsException → 429
   *   ServiceException         → 500
   *
   * ONE handler, ZERO redundancy. Adding a new exception class
   * that extends BaseException? It's automatically handled here.
   * Open/Closed principle — open for extension (new exceptions),
   * closed for modification (this handler doesn't change).
   */
  @ExceptionHandler(BaseException.class)
  public ResponseEntity<ApiResponse<Void>> handleBaseException(BaseException ex) {

    if (ex.getStatus().is5xxServerError()) {
      log.error("Server error: {}", ex.getMessage(), ex);
    } else {
      log.warn("Client error: {} - {}", ex.getStatus(), ex.getMessage());
    }

    return ResponseEntity
      .status(ex.getStatus())
      .body(ApiResponse.error(ex.getMessage()));
  }

  /**
   * Handles @Valid / @Validated failures on request DTOs.
   *
   * When you have a DTO like:
   *   class LoginRequest {
   *       @NotBlank String email;
   *       @NotBlank String password;
   *   }
   *
   * And the client sends { "email": "", "password": "" },
   * Spring throws MethodArgumentNotValidException BEFORE your
   * controller code even runs. This handler catches it.
   *
   * WHY extract only the first error?
   *   Simpler for the frontend. Instead of showing 5 errors at once,
   *   show one, let the user fix it, then show the next.
   *   This is a UX choice — you could change it to return all errors.
   *
   * WHY getDefaultMessage()?
   *   This returns the message you set in the annotation:
   *     @NotBlank(message = "Email is required")
   *   If no message is set, it returns the default like
   *   "must not be blank".
   */
  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<ApiResponse<Void>> handleValidationException(
    MethodArgumentNotValidException ex) {

    String message = ex.getBindingResult()
      .getFieldErrors()
      .stream()
      .findFirst()
      .map(error -> error.getField() + ": " + error.getDefaultMessage())
      .orElse("Validation failed");

    log.warn("Validation error: {}", message);

    return ResponseEntity
      .status(HttpStatus.BAD_REQUEST)
      .body(ApiResponse.error(message));
  }

  /**
   * Handles Spring Security's AccessDeniedException.
   *
   * When @PreAuthorize("hasRole('ADMIN')") blocks a ROLE_USER,
   * Spring Security throws AccessDeniedException. Without this
   * handler, Spring returns its default HTML error page — ugly
   * and useless for a REST API.
   *
   * WHY separate from ForbiddenException?
   *   ForbiddenException = thrown by YOUR code explicitly
   *   AccessDeniedException = thrown by Spring Security framework
   *   Both result in 403, but they come from different sources.
   */
  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<ApiResponse<Void>> handleAccessDenied(AccessDeniedException ex) {

    log.warn("Access denied: {}", ex.getMessage());

    return ResponseEntity
      .status(HttpStatus.FORBIDDEN)
      .body(ApiResponse.error("You do not have permission to perform this action"));
  }

  /**
   * Handles missing required headers.
   *
   * Example: if an endpoint requires "Authorization" header
   * and the client doesn't send it, this catches it.
   */
  @ExceptionHandler(MissingRequestHeaderException.class)
  public ResponseEntity<ApiResponse<Void>> handleMissingHeader(
    MissingRequestHeaderException ex) {

    log.warn("Missing header: {}", ex.getHeaderName());

    return ResponseEntity
      .status(HttpStatus.BAD_REQUEST)
      .body(ApiResponse.error("Missing required header: " + ex.getHeaderName()));
  }

  /**
   * THE SAFETY NET — catches everything else.
   *
   * If some unexpected exception slips through (NullPointerException,
   * database connection timeout, JSON parsing error, etc.), this
   * catches it so the client NEVER sees a raw stack trace.
   *
   * WHY "Something went wrong" instead of ex.getMessage()?
   *   Security. Internal error messages can leak sensitive info:
   *     - "Connection to postgres:5432 refused" → reveals DB host
   *     - "Column 'password_hash' not found" → reveals schema
   *     - "NullPointerException at UserService:142" → reveals code
   *
   *   The client gets a generic message. The actual error is logged
   *   server-side where only developers can see it.
   *
   * WHY log.error with the full exception (ex)?
   *   The second argument `ex` prints the full stack trace in logs.
   *   This is the ONLY way you'll debug these in production.
   *   Without the stack trace, you'd see "Something went wrong"
   *   in logs too — completely useless.
   */
  @ExceptionHandler(Exception.class)
  public ResponseEntity<ApiResponse<Void>> handleGenericException(Exception ex) {

    log.error("Unexpected error: {}", ex.getMessage(), ex);

    return ResponseEntity
      .status(HttpStatus.INTERNAL_SERVER_ERROR)
      .body(ApiResponse.error("Something went wrong. Please try again later."));
  }
}
