package com.railways.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Use when: an action conflicts with current state.
 *
 * Examples:
 *   - "User with email ravi@gmail.com already exists" (duplicate registration)
 *   - "This seat is already booked"
 *   - "Admin with this email is already active"
 */
public class ConflictException extends BaseException {

  public ConflictException(String message) {
    super(message, HttpStatus.CONFLICT);
  }
}
