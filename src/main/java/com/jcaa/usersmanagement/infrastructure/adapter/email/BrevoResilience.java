package com.jcaa.usersmanagement.infrastructure.adapter.email;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerConfig;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.net.SocketTimeoutException;
import java.util.concurrent.TimeoutException;
import org.springframework.http.HttpStatusCode;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;

/** Builds resilience components with a shared transient-failure classification. */
public final class BrevoResilience {

  private static final String INSTANCE_NAME = "brevo-email";

  private BrevoResilience() {}

  public static Retry retry(final BrevoResilienceConfig config) {
    final IntervalFunction backoff =
        IntervalFunction.ofExponentialBackoff(
            config.retryInitialDelay().toMillis(), config.retryMultiplier());
    final RetryConfig retryConfig =
        RetryConfig.custom()
            .maxAttempts(config.retryMaxAttempts())
            .intervalFunction(backoff)
            .retryOnException(BrevoResilience::isTransientFailure)
            .build();
    return Retry.of(INSTANCE_NAME, retryConfig);
  }

  public static CircuitBreaker circuitBreaker(final BrevoResilienceConfig config) {
    final CircuitBreakerConfig circuitBreakerConfig =
        CircuitBreakerConfig.custom()
            .failureRateThreshold(config.circuitBreakerFailureRateThreshold())
            .slidingWindowSize(config.circuitBreakerSlidingWindowSize())
            .minimumNumberOfCalls(config.circuitBreakerMinimumCalls())
            .waitDurationInOpenState(config.circuitBreakerOpenDuration())
            .recordException(BrevoResilience::isTransientFailure)
            .build();
    return CircuitBreaker.of(INSTANCE_NAME, circuitBreakerConfig);
  }

  static boolean isTransientFailure(final Throwable throwable) {
    if (throwable instanceof RestClientResponseException responseException) {
      final HttpStatusCode status = responseException.getStatusCode();
      return status.value() == 408 || status.value() == 429 || status.is5xxServerError();
    }
    if (throwable instanceof ResourceAccessException) {
      return true;
    }
    return throwable instanceof SocketTimeoutException || throwable instanceof TimeoutException;
  }
}
