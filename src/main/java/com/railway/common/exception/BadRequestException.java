package com.railway.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Use when: client sends bad input that fails YOUR business rules
 * (not validation annotations — those are handled separately).
 *
 * Examples:
 *   - "Cannot book a ticket for a past date"
 *   - "Password must not be same as email"
 *   - "Departure and arrival stations cannot be the same"
 */
public class BadRequestException extends BaseException {

  public BadRequestException(String message) {
    super(message, HttpStatus.BAD_REQUEST);
  }
}
