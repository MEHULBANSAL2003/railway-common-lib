package com.railway.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Use when: something unexpected happens in YOUR code that you
 * can identify but can't recover from.
 *
 * Examples:
 *   - Redis connection failed during token blacklist check
 *   - Kafka message publishing failed
 *   - External API (Google OAuth) returned unexpected response
 *
 * For truly unexpected errors (NullPointer, ArrayIndexOutOfBounds),
 * you don't throw this — those are caught by the generic
 * Exception handler in GlobalExceptionHandler.
 */
public class ServiceException extends BaseException {

  public ServiceException(String message) {
    super(message, HttpStatus.INTERNAL_SERVER_ERROR);
  }
}
