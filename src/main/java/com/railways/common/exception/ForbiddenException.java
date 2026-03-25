package com.railways.common.exception;

import org.springframework.http.HttpStatus;

/**
 * Use when: user is logged in but lacks the required role/permission.
 *
 * Examples:
 *   - A ROLE_USER trying to access /api/admin/users
 *   - An ADMIN trying to do something only SUPER_ADMIN can do
 */
public class ForbiddenException extends BaseException {

  public ForbiddenException(String message) {
    super(message, HttpStatus.FORBIDDEN);
  }
}
