package com.railway.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Use when: rate limiter blocks the request.
 *
 * Thrown by RateLimitFilter in common-lib.
 * The global handler converts this to a 429 response.
 */
public class TooManyRequestsException extends BaseException {

  public TooManyRequestsException(String message) {
    super(message, HttpStatus.TOO_MANY_REQUESTS);
  }
}
