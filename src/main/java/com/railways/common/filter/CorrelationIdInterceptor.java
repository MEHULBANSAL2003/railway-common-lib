package com.railways.common.filter;

import org.slf4j.MDC;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;

import java.io.IOException;

/**
 * Automatically forwards Correlation ID when one service calls another.
 *
 * PROBLEM:
 *   booking-service receives a request with correlationId "abc-123".
 *   It then calls train-service via RestTemplate.
 *   Without this interceptor, train-service gets NO correlation ID
 *   and generates a new one → the chain is broken.
 *
 * SOLUTION:
 *   Attach this interceptor to RestTemplate. It reads the correlationId
 *   from MDC (set by CorrelationIdFilter) and adds it as a header
 *   to the outgoing request. train-service's CorrelationIdFilter
 *   sees the header and reuses the same ID.
 *
 * USAGE (in any service's config):
 *
 *   @Bean
 *   public RestTemplate restTemplate(CorrelationIdInterceptor interceptor) {
 *       RestTemplate restTemplate = new RestTemplate();
 *       restTemplate.setInterceptors(List.of(interceptor));
 *       return restTemplate;
 *   }
 *
 * HOW IT WORKS:
 *   RestTemplate calls intercept() for every outgoing HTTP request.
 *   We add the header, then call execution.execute() to actually
 *   send the request. It's like a "filter" but for outgoing calls
 *   instead of incoming ones.
 */
public class CorrelationIdInterceptor implements ClientHttpRequestInterceptor {

  @Override
  public ClientHttpResponse intercept(
    HttpRequest request,
    byte[] body,
    ClientHttpRequestExecution execution) throws IOException {

    String correlationId = MDC.get(CorrelationIdFilter.CORRELATION_ID_MDC_KEY);

    if (correlationId != null) {
      request.getHeaders().set(CorrelationIdFilter.CORRELATION_ID_HEADER, correlationId);
    }

    return execution.execute(request, body);
  }
}
