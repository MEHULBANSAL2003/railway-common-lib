package com.railways.common.security;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

/**
 * Represents the currently authenticated identity (admin or user).
 *
 * Stored in SecurityContext by JwtAuthenticationFilter.
 * Any controller can access it via:
 *
 *   @GetMapping("/profile")
 *   public ApiResponse<?> getProfile(@AuthenticationPrincipal AuthPrincipal principal) {
 *       Long id = principal.getId();
 *       String type = principal.getType();  // "admin" or "user"
 *       String role = principal.getRole();  // "SUPER_ADMIN", "ADMIN", "USER"
 *   }
 *
 * WHY "AuthPrincipal" not "UserPrincipal"?
 *   This class serves both admins and users. The name should be
 *   generic — it represents "whoever is authenticated", not
 *   specifically a user or an admin.
 *
 * WHY immutable (@Getter only, no @Setter)?
 *   The principal represents who made THIS request. It should never
 *   change mid-request. Immutability makes this guarantee explicit.
 */
@Getter
@Builder
@AllArgsConstructor
public class AuthPrincipal {

  private final Long id;
  private final String email;
  private final String role;
  private final String type;     // "admin" or "user"

  /**
   * Quick check: is this an admin token?
   */
  public boolean isAdmin() {
    return "admin".equals(type);
  }

  /**
   * Quick check: is this a user token?
   */
  public boolean isUser() {
    return "user".equals(type);
  }
}
