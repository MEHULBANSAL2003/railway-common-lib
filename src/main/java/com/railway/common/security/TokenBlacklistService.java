package com.railway.common.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "spring.data.redis.host")
public class TokenBlacklistService {

  private final RedisTemplate<String, String> redisTemplate;

  private static final String KEY_PREFIX = "auth:session-cutoff:";

  public void setCutoff(String ownerType, Long ownerId, Duration ttl) {
    String key = buildKey(ownerType, ownerId);
    String cutoffTimestamp = String.valueOf(Instant.now().toEpochMilli());

    redisTemplate.opsForValue().set(key, cutoffTimestamp, ttl);
    log.debug("Session cutoff set: {} → {}", key, cutoffTimestamp);
  }

  public void clearCutoff(String ownerType, Long ownerId) {
    String key = buildKey(ownerType, ownerId);
    redisTemplate.delete(key);
    log.debug("Session cutoff cleared: {}", key);
  }

  private String buildKey(String ownerType, Long ownerId) {
    return KEY_PREFIX + ownerType + ":" + ownerId;
  }

  public boolean isBlacklisted(String ownerType, Long ownerId, Instant issuedAt) {
    String key = buildKey(ownerType, ownerId);
    String cutoffStr = redisTemplate.opsForValue().get(key);

    if (cutoffStr == null) {
      return false;
    }

    Instant cutoff = Instant.ofEpochMilli(Long.parseLong(cutoffStr));
    return issuedAt.isBefore(cutoff);
  }
}
