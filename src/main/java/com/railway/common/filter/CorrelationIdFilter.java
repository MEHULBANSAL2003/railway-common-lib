package com.railway.common.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Manages correlation ID for request tracing across services.
 *
 * WHAT IT DOES:
 *   1. Checks if incoming request already has X-Correlation-ID header
 *      (set by another service that called us)
 *   2. If yes → reuse it (we're part of a chain)
 *   3. If no → generate a new UUID (we're the first service)
 *   4. Put it in MDC so every log line includes it automatically
 *   5. Add it to the response header (for debugging from frontend)
 *   6. Clean up MDC after request completes
 *
 * WHY @Order(Ordered.HIGHEST_PRECEDENCE)?
 *   This filter must run BEFORE all other filters — before JWT filter,
 *   before rate limit filter, before everything. Because those filters
 *   also produce logs, and we want the correlation ID present in ALL
 *   logs from the very beginning of request processing.
 *
 * WHAT IS MDC?
 *   MDC (Mapped Diagnostic Context) is a thread-local map provided
 *   by SLF4J/Logback. When you put a key-value pair in MDC, every
 *   log statement on that thread automatically includes it.
 *
 *   Put "correlationId" = "abc-123" in MDC, and:
 *     log.info("Processing request")
 *   Becomes:
 *     {"correlationId": "abc-123", "message": "Processing request", ...}
 *
 *   No need to manually pass the ID around. It's automatic.
 *
 * WHY clean up in finally block?
 *   MDC is thread-local. In a thread pool (which Spring uses),
 *   threads are reused. If we don't clean up, the NEXT request
 *   that gets this thread would inherit the OLD correlation ID.
 *   That would link unrelated requests together — a debugging
 *   nightmare worse than having no correlation ID at all.
 */
@Slf4j
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class CorrelationIdFilter extends OncePerRequestFilter {

  public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";
  public static final String CORRELATION_ID_MDC_KEY = "correlationId";

  @Override
  protected void doFilterInternal(
    HttpServletRequest request,
    HttpServletResponse response,
    FilterChain filterChain) throws ServletException, IOException {

    try {
      // Step 1: Check if caller already set a correlation ID
      String correlationId = request.getHeader(CORRELATION_ID_HEADER);

      // Step 2: No header? Generate new one (we're the entry point)
      if (correlationId == null || correlationId.isBlank()) {
        correlationId = UUID.randomUUID().toString();
      }

      // Step 3: Put in MDC → all logs on this thread now include it
      MDC.put(CORRELATION_ID_MDC_KEY, correlationId);

      // Step 4: Add to response header → frontend/caller can see it
      response.setHeader(CORRELATION_ID_HEADER, correlationId);

      // Step 5: Continue filter chain
      filterChain.doFilter(request, response);

    } finally {
      // Step 6: ALWAYS clean up MDC — even if an exception occurred
      MDC.remove(CORRELATION_ID_MDC_KEY);
    }
  }
}
