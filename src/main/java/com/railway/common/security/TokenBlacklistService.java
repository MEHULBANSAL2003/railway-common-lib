package com.railway.common.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "spring.data.redis.host")
public class TokenBlacklistService {

  private final StringRedisTemplate redisTemplate;

  private static final String KEY_PREFIX = "auth:session-cutoff:";

  /**
   * Invalidate all tokens issued before NOW for a specific owner.
   *
   * After this call, any access token with iat < now is rejected.
   * The Redis key auto-expires after ttl (= access token lifetime),
   * because after that, old tokens expire naturally anyway.
   *
   * @param ownerType "admin" or "user"
   * @param ownerId   the admin_id or user_id
   * @param ttl       how long to keep this cutoff (= access token expiry)
   */
  public void setCutoff(String ownerType, Long ownerId, Duration ttl) {
    try {
      String key = buildKey(ownerType, ownerId);
      // Use epoch seconds (not millis) to match JWT's iat precision.
      // JWT iat is stored in seconds — using millis here would cause
      // tokens issued in the same second as the cutoff to appear
      // "before" the cutoff due to millisecond truncation.
      String cutoffTimestamp = String.valueOf(Instant.now().getEpochSecond());
      redisTemplate.opsForValue().set(key, cutoffTimestamp, ttl);
      log.debug("Session cutoff set: {} → {}", key, cutoffTimestamp);
    } catch (Exception ex) {
      // Redis is down — log and continue. Login still works,
      // just without instant token revocation. Old tokens
      // expire naturally in 15 min.
      log.warn("Failed to set session cutoff in Redis: {}", ex.getMessage());
    }
  }

  /**
   * Check if a token should be rejected.
   *
   * Compares the token's issued-at time against the stored cutoff.
   * If the token was issued BEFORE the cutoff → blacklisted.
   * If no cutoff exists → not blacklisted (no active revocation).
   *
   * @param ownerType "admin" or "user"
   * @param ownerId   the owner's ID
   * @param issuedAt  the token's iat claim
   * @return true if the token should be REJECTED
   */
  public boolean isBlacklisted(String ownerType, Long ownerId, Instant issuedAt) {
    try {
      String key = buildKey(ownerType, ownerId);
      String cutoffStr = redisTemplate.opsForValue().get(key);

      if (cutoffStr == null) {
        return false;
      }

      Instant cutoff = Instant.ofEpochSecond(Long.parseLong(cutoffStr));
      return issuedAt.isBefore(cutoff);
    } catch (Exception ex) {
      // Redis is down — can't check blacklist.
      // Allow the request through. Tokens live until natural expiry.
      log.warn("Failed to check blacklist in Redis: {}", ex.getMessage());
      return false;
    }
  }

  /**
   * Remove the cutoff — stop blacklisting for this owner.
   * Useful if you need to manually clear a blacklist entry.
   */
  public void clearCutoff(String ownerType, Long ownerId) {
    try {
      String key = buildKey(ownerType, ownerId);
      redisTemplate.delete(key);
      log.debug("Session cutoff cleared: {}", key);
    } catch (Exception ex) {
      log.warn("Failed to clear session cutoff in Redis: {}", ex.getMessage());
    }
  }

  private String buildKey(String ownerType, Long ownerId) {
    return KEY_PREFIX + ownerType + ":" + ownerId;
  }
}
