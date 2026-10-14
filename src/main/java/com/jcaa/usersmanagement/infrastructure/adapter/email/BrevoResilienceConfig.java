package com.jcaa.usersmanagement.infrastructure.adapter.email;

import java.time.Duration;
import java.util.Objects;

/** Retry and circuit-breaker settings for Brevo's synchronous HTTP API. */
public record BrevoResilienceConfig(
    int retryMaxAttempts,
    Duration retryInitialDelay,
    double retryMultiplier,
    float circuitBreakerFailureRateThreshold,
    int circuitBreakerSlidingWindowSize,
    int circuitBreakerMinimumCalls,
    Duration circuitBreakerOpenDuration) {

  public BrevoResilienceConfig {
    if (retryMaxAttempts < 1) {
      throw new IllegalArgumentException("BREVO_RETRY_MAX_ATTEMPTS must be at least 1.");
    }
    requirePositive(retryInitialDelay, "BREVO_RETRY_INITIAL_DELAY_MS");
    if (retryMultiplier < 1.0) {
      throw new IllegalArgumentException("BREVO_RETRY_MULTIPLIER must be at least 1.");
    }
    if (circuitBreakerFailureRateThreshold <= 0 || circuitBreakerFailureRateThreshold > 100) {
      throw new IllegalArgumentException(
          "BREVO_CIRCUIT_FAILURE_THRESHOLD must be greater than 0 and at most 100.");
    }
    if (circuitBreakerSlidingWindowSize < 1) {
      throw new IllegalArgumentException("BREVO_CIRCUIT_WINDOW_SIZE must be at least 1.");
    }
    if (circuitBreakerMinimumCalls < 1
        || circuitBreakerMinimumCalls > circuitBreakerSlidingWindowSize) {
      throw new IllegalArgumentException(
          "BREVO_CIRCUIT_MINIMUM_CALLS must be between 1 and the window size.");
    }
    requirePositive(circuitBreakerOpenDuration, "BREVO_CIRCUIT_OPEN_DURATION_MS");
  }

  private static void requirePositive(final Duration duration, final String property) {
    if (Objects.isNull(duration) || duration.isZero() || duration.isNegative()) {
      throw new IllegalArgumentException(property + " must be positive.");
    }
  }
}
