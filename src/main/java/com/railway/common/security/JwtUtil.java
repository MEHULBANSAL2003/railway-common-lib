package com.railway.common.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

/**
 * Handles JWT creation and validation.
 *
 * LIVES IN COMMON-LIB because:
 *   - auth-service CREATES tokens (uses generateAccessToken, generateRefreshToken)
 *   - ALL other services VALIDATE tokens (uses validateToken, extractClaims)
 *   One class, no duplication.
 *
 * WHY @Component?
 *   Spring manages this as a bean. It reads config values from
 *   application.yml via @Value, so it must be Spring-managed.
 *   Each service provides its own app.jwt.secret in its config,
 *   but the code is shared.
 *
 * WHY a single SecretKey?
 *   We use HMAC-SHA256 (symmetric). Same key signs and verifies.
 *   All services share the same secret (via environment variable).
 *   In a more advanced setup, you'd use RSA (asymmetric) — auth-service
 *   signs with a private key, others verify with a public key.
 *   But HMAC is simpler and fine for this scale.
 */
@Slf4j
@Component
public class JwtUtil {

  @Value("${app.jwt.secret}")
  private String secret;

  @Value("${app.jwt.access-token-expiry}")
  private long accessTokenExpiry;

  @Value("${app.jwt.refresh-token-expiry}")
  private long refreshTokenExpiry;

  @Value("${app.jwt.issuer}")
  private String issuer;

  /**
   * The signing key, derived from the secret string.
   *
   * WHY not use the raw string directly?
   *   JJWT requires a SecretKey object, not a string.
   *   Keys.hmacShaKeyFor() converts the string to a proper
   *   HMAC-SHA key. It also validates that the key is long enough
   *   (minimum 256 bits for HS256 = 32 characters).
   *
   * WHY @PostConstruct?
   *   @Value fields are injected AFTER the constructor runs.
   *   So in the constructor, `secret` would be null.
   *   @PostConstruct runs after all @Value fields are set.
   *   We compute the key once here, not on every call.
   */
  private SecretKey signingKey;

  @PostConstruct
  public void init() {
    this.signingKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
  }

  // ─────────────────────────────────────────────
  // TOKEN CREATION (used only by auth-service)
  // ─────────────────────────────────────────────

  /**
   * Generates an access token.
   *
   * @param userId  the user or admin ID (from DB)
   * @param email   user's email
   * @param role    SUPER_ADMIN, ADMIN, or USER
   * @param type    "admin" or "user" — tells the JWT filter which table to check
   *
   * The token payload will look like:
   * {
   *   "sub": "1",              ← subject = user ID (standard JWT claim)
   *   "email": "ravi@gmail.com",
   *   "role": "USER",
   *   "type": "user",
   *   "iss": "railway-auth",   ← issuer (standard claim)
   *   "iat": 1711368000,       ← issued at (standard claim)
   *   "exp": 1711368900        ← expires at (standard claim)
   * }
   *
   * WHY String for userId?
   *   JWT subject claim is always a string. Even though your DB ID
   *   is a Long, converting here keeps the JWT spec-compliant.
   *   We parse it back to Long when extracting.
   */
  public String generateAccessToken(Long userId, String email, String role, String type) {
    return buildToken(
      Map.of(
        "email", email,
        "role", role,
        "type", type
      ),
      String.valueOf(userId),
      accessTokenExpiry
    );
  }

  /**
   * Generates a refresh token.
   *
   * WHY fewer claims than access token?
   *   Refresh tokens are ONLY used to get new access tokens.
   *   They're never sent to business APIs (train-service, booking-service).
   *   So they don't need role/email — auth-service will look up
   *   the latest role from the DB when issuing a new access token.
   *   This also means if an admin's role changes, the next refresh
   *   picks up the new role automatically.
   */
  public String generateRefreshToken(Long userId, String type) {
    return buildToken(
      Map.of("type", type),
      String.valueOf(userId),
      refreshTokenExpiry
    );
  }

  /**
   * Core token builder. Private — external code uses the specific methods above.
   *
   * WHY a separate method?
   *   Both generateAccessToken and generateRefreshToken follow the
   *   same pattern. DRY — extract the common logic.
   *
   * HOW SIGNING WORKS:
   *   Jwts.builder() creates header + payload, then .signWith(signingKey)
   *   computes HMAC-SHA256(header.payload, signingKey) and appends it
   *   as the third part. The result is: header.payload.signature
   */
  private String buildToken(Map<String, String> claims, String subject, long expiryMs) {
    long now = System.currentTimeMillis();

    return Jwts.builder()
      .claims(claims)
      .subject(subject)
      .issuer(issuer)
      .issuedAt(new Date(now))
      .expiration(new Date(now + expiryMs))
      .signWith(signingKey)
      .compact();
  }

  // ─────────────────────────────────────────────
  // TOKEN VALIDATION (used by ALL services)
  // ─────────────────────────────────────────────

  /**
   * Validates a token and returns true/false.
   *
   * WHAT CAN GO WRONG:
   *   1. Expired → ExpiredJwtException (token's exp < current time)
   *   2. Tampered → SignatureException (signature doesn't match)
   *   3. Malformed → MalformedJwtException (not a valid JWT string)
   *   4. Wrong key → SecurityException (signed with different secret)
   *
   * All of these are subclasses of JwtException, so one catch handles all.
   *
   * WHY return boolean instead of throwing?
   *   The JWT filter calls this. If invalid, the filter simply
   *   doesn't set the SecurityContext — the request continues
   *   unauthenticated. Spring Security then decides: if the endpoint
   *   is public, it's fine. If protected, Spring returns 401.
   *   This keeps the filter clean — no exception handling there.
   */
  public boolean validateToken(String token) {
    try {
      extractAllClaims(token);
      return true;
    } catch (ExpiredJwtException ex) {
      log.warn("JWT expired: {}", ex.getMessage());
    } catch (JwtException ex) {
      log.warn("JWT invalid: {}", ex.getMessage());
    }
    return false;
  }

  /**
   * Extracts all claims from a valid token.
   *
   * WHY parse every time instead of caching?
   *   Parsing is fast (~0.1ms). Caching would mean storing tokens
   *   in memory, which defeats the stateless nature of JWT.
   *   The signature verification happens inside parseSignedClaims().
   *
   * WHAT HAPPENS INSIDE:
   *   1. Splits the token into header.payload.signature
   *   2. Re-computes HMAC-SHA256(header.payload, signingKey)
   *   3. Compares with the signature in the token
   *   4. If match → returns the payload (claims)
   *   5. If mismatch → throws JwtException
   */
  public Claims extractAllClaims(String token) {
    return Jwts.parser()
      .verifyWith(signingKey)
      .requireIssuer(issuer)
      .build()
      .parseSignedClaims(token)
      .getPayload();
  }

  // ─────────────────────────────────────────────
  // CONVENIENCE EXTRACTORS
  // Used by JWT filter and services to read specific claims
  // without manually parsing Claims object every time.
  // ─────────────────────────────────────────────

  /**
   * Extracts ID from token (admin ID or user ID).
   * The "sub" (subject) claim holds the ID as a string.
   * Use extractType() to know which table this ID belongs to.
   */
  public Long extractId(String token) {
    return Long.parseLong(extractAllClaims(token).getSubject());
  }

  /**
   * Extracts email from token.
   * Only present in access tokens (not refresh tokens).
   */
  public String extractEmail(String token) {
    return extractAllClaims(token).get("email", String.class);
  }

  /**
   * Extracts role from token.
   * Returns "SUPER_ADMIN", "ADMIN", or "USER".
   * Only present in access tokens.
   */
  public String extractRole(String token) {
    return extractAllClaims(token).get("role", String.class);
  }

  /**
   * Extracts type from token.
   * Returns "admin" or "user".
   * Present in both access and refresh tokens.
   *
   * WHY THIS MATTERS:
   *   When the JWT filter validates a request, it needs to know:
   *   is this an admin token or a user token? Because admin data
   *   is in the `admins` table and user data is in the `users` table.
   *   This claim tells the filter which context to use.
   */
  public String extractType(String token) {
    return extractAllClaims(token).get("type", String.class);
  }

  /**
   * Checks if a token is expired.
   *
   * WHY A SEPARATE METHOD?
   *   Sometimes you want to know specifically "is this expired?"
   *   vs "is this invalid for some other reason?".
   *   Example: if expired, prompt the frontend to use refresh token.
   *   If tampered, force a full re-login.
   */
  public boolean isTokenExpired(String token) {
    try {
      Date expiration = extractAllClaims(token).getExpiration();
      return expiration.before(new Date());
    } catch (ExpiredJwtException ex) {
      return true;
    } catch (JwtException ex) {
      return true;
    }
  }
}
