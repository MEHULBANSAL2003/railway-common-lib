package com.railways.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Use when: no token, expired token, invalid token, wrong credentials.
 *
 * 401 means "I don't know who you are."
 * This is different from 403 (I know who you are, but you can't do this).
 */
public class UnauthorizedException extends BaseException {

  public UnauthorizedException(String message) {
    super(message, HttpStatus.UNAUTHORIZED);
  }
}
