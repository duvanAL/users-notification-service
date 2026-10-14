package com.jcaa.usersmanagement.infrastructure.adapter.email;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;

public record BrevoConfig(
    String baseUrl,
    String apiKey,
    String fromAddress,
    String fromName,
    Duration connectTimeout,
    Duration readTimeout) {

  public BrevoConfig {
    requireText(baseUrl, "BREVO_BASE_URL");
    requireText(apiKey, "BREVO_API_KEY");
    requireText(fromAddress, "BREVO_FROM_ADDRESS");
    requireText(fromName, "BREVO_FROM_NAME");
    final URI uri = URI.create(baseUrl);
    if (!uri.isAbsolute() || Objects.isNull(uri.getHost())) {
      throw new IllegalArgumentException("BREVO_BASE_URL must be an absolute URL.");
    }
    if (Objects.isNull(connectTimeout) || connectTimeout.isNegative() || connectTimeout.isZero()) {
      throw new IllegalArgumentException("BREVO_CONNECT_TIMEOUT must be positive.");
    }
    if (Objects.isNull(readTimeout) || readTimeout.isNegative() || readTimeout.isZero()) {
      throw new IllegalArgumentException("BREVO_READ_TIMEOUT must be positive.");
    }
  }

  private static void requireText(final String value, final String property) {
    if (Objects.isNull(value) || value.isBlank()) {
      throw new IllegalArgumentException(property + " is required when Brevo email is enabled.");
    }
  }
}
