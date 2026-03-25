package com.railway.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Use when: a lookup by ID, email, phone, etc. returns nothing.
 *
 * WHY a formatted constructor?
 *   Instead of: throw new ResourceNotFoundException("User not found with id: 5")
 *   You write:  throw new ResourceNotFoundException("User", "id", 5)
 *   Output:     "User not found with id: 5"
 *
 *   Consistent message format across the entire platform.
 *   Every 404 reads the same way. No typos, no inconsistencies.
 */
public class ResourceNotFoundException extends BaseException {

  public ResourceNotFoundException(String resource, String field, Object value) {
    super(String.format("%s not found with %s: %s", resource, field, value),
      HttpStatus.NOT_FOUND);
  }
}
