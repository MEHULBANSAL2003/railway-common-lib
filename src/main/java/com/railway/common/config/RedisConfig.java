package com.railway.common.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

/**
 * Redis configuration shared by all services.
 *
 * WHY in common-lib?
 *   Every service that uses Redis needs the same template setup.
 *   Without this config, Spring's default RedisTemplate uses
 *   Java serialization — keys look like garbage in RedisInsight:
 *     \xac\xed\x00\x05t\x00\x0eauth:rate-limit
 *   With StringRedisSerializer, keys are clean readable strings:
 *     auth:rate-limit:192.168.1.1
 *
 * WHY @ConditionalOnProperty?
 *   This bean is only created when spring.data.redis.host is set
 *   in the config. In dev, if you haven't configured Redis yet,
 *   this class is simply skipped — no errors, no crashes.
 *   Services that don't use Redis at all won't have this property,
 *   so they won't create this bean.
 */
@Configuration
@ConditionalOnProperty(name = "spring.data.redis.host")
public class RedisConfig {

  /**
   * Custom RedisTemplate with String serialization.
   *
   * WHY customize?
   *   Default RedisTemplate<Object, Object> uses JdkSerializationRedisSerializer.
   *   This serializes keys and values as Java byte arrays — unreadable
   *   in any Redis GUI tool and not compatible across languages.
   *
   *   StringRedisSerializer stores everything as plain UTF-8 strings.
   *   When you do redis.set("auth:blacklist:abc123", "true"),
   *   it stores exactly that — readable in RedisInsight, compatible
   *   with any Redis client in any language.
   *
   * WHY RedisTemplate<String, String>?
   *   All our Redis use cases store string keys and string values:
   *     rate-limit: key="auth:rate-limit:1.2.3.4" value="5"
   *     blacklist:  key="auth:blacklist:eyJhb..." value="true"
   *   If we ever need to store objects, we'll serialize to JSON
   *   string first, then store. Keeps Redis simple and debuggable.
   */
  @Bean
  public RedisTemplate<String, String> redisTemplate(RedisConnectionFactory connectionFactory) {

    RedisTemplate<String, String> template = new RedisTemplate<>();
    template.setConnectionFactory(connectionFactory);

    // Keys are always strings
    template.setKeySerializer(new StringRedisSerializer());
    template.setHashKeySerializer(new StringRedisSerializer());

    // Values are always strings (we'll JSON-serialize objects manually if needed)
    template.setValueSerializer(new StringRedisSerializer());
    template.setHashValueSerializer(new StringRedisSerializer());

    template.afterPropertiesSet();
    return template;
  }
}
