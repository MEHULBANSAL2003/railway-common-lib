package com.railways.common.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Base class for ALL custom exceptions in the platform.
 *
 * WHY a base class?
 *   Every custom exception needs two things:
 *     1. A human-readable message ("User not found")
 *     2. An HTTP status code (404)
 *
 *   Without a base class, each exception would repeat this pattern.
 *   With it, we define it once. DRY.
 *
 * WHY extend RuntimeException (unchecked) not Exception (checked)?
 *   Checked exceptions force every method in the call chain to
 *   declare `throws`. In a Spring app, that means:
 *     Repository → throws → Service → throws → Controller → throws
 *   Pointless ceremony. Our GlobalExceptionHandler catches everything
 *   at the top anyway. Unchecked exceptions bubble up silently.
 */
@Getter
public abstract class BaseException extends RuntimeException {

  private final HttpStatus status;

  protected BaseException(String message, HttpStatus status) {
    super(message);
    this.status = status;
  }
}
