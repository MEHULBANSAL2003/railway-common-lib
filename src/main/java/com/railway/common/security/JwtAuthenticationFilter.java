package com.railway.common.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Intercepts every HTTP request, checks for a JWT token,
 * and sets up Spring Security context if valid.
 *
 * WHERE THIS SITS:
 *   HTTP Request → CORS → RateLimit → [THIS FILTER] → Authorization → Controller
 *
 * WHY extend OncePerRequestFilter?
 *   A regular Filter might execute multiple times per request
 *   (e.g., if the request is forwarded internally). OncePerRequestFilter
 *   guarantees exactly ONE execution per request. Standard practice
 *   for authentication filters.
 *
 * WHY in common-lib?
 *   Every service needs this exact same filter. auth-service,
 *   train-service, booking-service — all need to validate tokens
 *   on incoming requests. Write once, share everywhere.
 *
 * WHAT THIS FILTER DOES NOT DO:
 *   - Does NOT check if the user has the right ROLE for the endpoint.
 *     That's Spring Security's authorization layer (@PreAuthorize).
 *   - Does NOT block requests without a token. Public endpoints
 *     (login, register) have no token — this filter just skips them.
 *   - Does NOT create tokens. That's auth-service's job.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

  private final JwtUtil jwtUtil;
  private final Optional<TokenBlacklistService> blacklistService;

  /**
   * Core filter logic. Runs on EVERY request.
   *
   * FLOW:
   *   1. Extract token from "Authorization: Bearer xxx" header
   *   2. No token? → skip, let the request continue unauthenticated
   *      (Spring Security handles public vs protected endpoint distinction)
   *   3. Has token but invalid/expired? → write 401 immediately, stop chain
   *   4. Has token, valid, already authenticated? → skip
   *   5. Valid token → attempt to set SecurityContext (includes blacklist check)
   *   6. Blacklisted? → write 401 immediately, stop chain
   *   7. All good → continue to next filter
   *
   * WHY "skip" for no-token but "401" for bad-token?
   *   No token = could be a public endpoint (login, register). We don't
   *   know yet, so we let Spring Security's authorization layer decide.
   *
   *   Bad token = the client explicitly tried to authenticate and failed.
   *   There's no scenario where a present-but-invalid token should proceed.
   *   Writing 401 here is explicit and correct — we don't leave it to
   *   Spring Security, which would otherwise treat the request as anonymous
   *   and return 403 (AccessDeniedException) instead of 401.
   */
  @Override
  protected void doFilterInternal(
    HttpServletRequest request,
    HttpServletResponse response,
    FilterChain filterChain) throws ServletException, IOException {

    // Step 1: Extract token from header
    String token = extractToken(request);

    // Step 2: No token → skip, let Spring Security handle it
    if (token == null) {
      filterChain.doFilter(request, response);
      return;
    }

    // Step 3: Token present but invalid/expired → 401 immediately
    if (!jwtUtil.validateToken(token)) {
      sendUnauthorized(response, "Invalid or expired token");
      return;
    }

    // Step 4: Token is valid → check if SecurityContext is already set
    // (could happen if another filter already authenticated)
    if (SecurityContextHolder.getContext().getAuthentication() != null) {
      filterChain.doFilter(request, response);
      return;
    }

    // Step 5 & 6: Build Authentication object and set SecurityContext.
    // Returns false if token is blacklisted → 401 immediately.
    boolean contextSet = setSecurityContext(token, request);
    if (!contextSet) {
      sendUnauthorized(response, "Token has been revoked");
      return;
    }

    // Step 7: Continue to next filter
    filterChain.doFilter(request, response);
  }

  /**
   * Extracts the JWT from the Authorization header.
   *
   * Expected format: "Authorization: Bearer eyJhbGciOiJ..."
   *
   * WHY "Bearer"?
   *   It's the standard prefix for token-based auth (RFC 6750).
   *   "Bearer" means "whoever bears (carries) this token gets access."
   *   Every REST API follows this convention — your React frontend
   *   will send: axios.defaults.headers.common['Authorization'] = `Bearer ${token}`;
   *
   * Returns null if:
   *   - No Authorization header
   *   - Header doesn't start with "Bearer "
   *   - Token part is empty
   */
  private String extractToken(HttpServletRequest request) {
    String header = request.getHeader("Authorization");

    if (header == null || !header.startsWith("Bearer ")) {
      return null;
    }

    String token = header.substring(7).trim();
    return token.isEmpty() ? null : token;
  }

  /**
   * Writes a 401 Unauthorized JSON response and stops the filter chain.
   *
   * WHY write directly to the response here?
   *   Once we know the token is bad, there's nothing downstream that
   *   can help. Writing 401 here short-circuits the chain cleanly,
   *   rather than letting Spring Security misinterpret the situation
   *   as an anonymous request and return 403 instead.
   *
   * IMPORTANT: Do NOT call filterChain.doFilter() after this method.
   */
  private void sendUnauthorized(HttpServletResponse response, String message) throws IOException {
    response.setStatus(HttpServletResponse.SC_UNAUTHORIZED); // 401
    response.setContentType("application/json");
    response.getWriter().write("{\"error\": \"" + message + "\"}");
  }

  /**
   * Creates an Authentication object and sets it in SecurityContext.
   *
   * WHAT IS SecurityContext?
   *   It's a thread-local storage that holds "who is the current user?"
   *   for the duration of this request. Once set, any code downstream
   *   (controllers, services) can access it via:
   *     SecurityContextHolder.getContext().getAuthentication()
   *
   *   Or more conveniently with @AuthenticationPrincipal in controllers.
   *
   * WHAT IS UsernamePasswordAuthenticationToken?
   *   Despite the name, it's not just for username/password auth.
   *   It's Spring's general-purpose "authenticated user" object.
   *   We use it because Spring Security expects this type.
   *
   *   Constructor: (principal, credentials, authorities)
   *     - principal: the user identity (we use a custom UserPrincipal)
   *     - credentials: null (we already validated via JWT, no password needed)
   *     - authorities: the user's roles (for @PreAuthorize checks)
   *
   * WHY store id, email, type, role in UserPrincipal?
   *   So any controller can access the current user's info without
   *   re-parsing the JWT or querying the database:
   *     @AuthenticationPrincipal UserPrincipal principal
   *     principal.getId()    → 1
   *     principal.getType()  → "admin"
   *     principal.getRole()  → "SUPER_ADMIN"
   *
   * RETURNS:
   *   true  → context set successfully, request may proceed
   *   false → token is blacklisted or an error occurred; caller must 401
   */
  private boolean setSecurityContext(String token, HttpServletRequest request) {
    try {
      Long id = jwtUtil.extractId(token);
      String email = jwtUtil.extractEmail(token);
      String role = jwtUtil.extractRole(token);
      String type = jwtUtil.extractType(token);

      if (blacklistService.isPresent()) {
        Instant issuedAt = jwtUtil.extractAllClaims(token)
          .getIssuedAt()
          .toInstant();

        if (blacklistService.get().isBlacklisted(type, id, issuedAt)) {
          log.debug("Token blacklisted for {} id={}", type, id);
          return false;
        }
      }

      AuthPrincipal principal = AuthPrincipal.builder()
        .id(id)
        .email(email)
        .role(role)
        .type(type)
        .build();

      UsernamePasswordAuthenticationToken authentication =
        new UsernamePasswordAuthenticationToken(
          principal,
          null,
          List.of(new SimpleGrantedAuthority("ROLE_" + role))
        );

      SecurityContextHolder.getContext().setAuthentication(authentication);

      log.debug("Authenticated {} (id={}, role={})", type, id, role);
      return true;

    } catch (Exception ex) {
      log.warn("Failed to set security context: {}", ex.getMessage());
      // Don't throw — return false so caller sends 401.
      return false;
    }
  }
}
