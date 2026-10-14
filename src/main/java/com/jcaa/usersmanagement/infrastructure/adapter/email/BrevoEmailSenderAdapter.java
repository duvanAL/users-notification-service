package com.jcaa.usersmanagement.infrastructure.adapter.email;

import com.jcaa.usersmanagement.application.port.out.EmailSenderPort;
import com.jcaa.usersmanagement.domain.exception.EmailSenderException;
import com.jcaa.usersmanagement.domain.model.EmailDestinationModel;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/** Sends transactional email through Brevo's HTTPS API. */
@Slf4j
public final class BrevoEmailSenderAdapter implements EmailSenderPort {

  private static final String SEND_EMAIL_PATH = "/v3/smtp/email";
  private static final String LOG_SENT =
      "[BrevoEmailSenderAdapter] Brevo aceptó un correo transaccional.";

  private final BrevoConfig config;
  private final RestClient restClient;
  private final Retry retry;
  private final CircuitBreaker circuitBreaker;

  public BrevoEmailSenderAdapter(
      final BrevoConfig config,
      final RestClient restClient,
      final Retry retry,
      final CircuitBreaker circuitBreaker) {
    this.config = config;
    this.restClient = restClient;
    this.retry = retry;
    this.circuitBreaker = circuitBreaker;
  }

  @Override
  public void send(final EmailDestinationModel destination) {
    send(destination, UUID.randomUUID().toString());
  }

  @Override
  public void send(final EmailDestinationModel destination, final String idempotencyKey) {
    final BrevoEmailRequest request =
        new BrevoEmailRequest(
            new BrevoEmailRequest.Sender(config.fromAddress(), config.fromName()),
            List.of(
                new BrevoEmailRequest.Recipient(
                    destination.getDestinationEmail(), destination.getDestinationName())),
            destination.getSubject(),
            destination.getBody(),
            Map.of("idempotencyKey", idempotencyKey));
    try {
      final Runnable sendRequest =
          () -> {
            try {
              restClient.post().uri(SEND_EMAIL_PATH).body(request).retrieve().toBodilessEntity();
            } catch (final RestClientResponseException exception) {
              // Brevo returns duplicate_parameter when it has already accepted this key.
              // Treat that response as success so Kafka redelivery is acknowledged.
              if (!isDuplicateIdempotencyKey(exception)) {
                throw exception;
              }
            }
          };
      // The breaker observes the final result after bounded retries, not every attempt.
      CircuitBreaker.decorateRunnable(circuitBreaker, Retry.decorateRunnable(retry, sendRequest))
          .run();
      log.info(LOG_SENT);
    } catch (final RestClientException | CallNotPermittedException exception) {
      // Do not log the response body: provider errors can contain recipient or account details.
      final String failure =
          exception instanceof RestClientResponseException responseException
              ? "HTTP " + responseException.getStatusCode().value()
              : exception.getClass().getSimpleName();
      throw EmailSenderException.becauseSendFailed(
          new IllegalStateException("Brevo API request failed: " + failure + "."));
    }
  }

  private static boolean isDuplicateIdempotencyKey(final RestClientResponseException exception) {
    return exception.getStatusCode().value() == 400
        && exception.getResponseBodyAsString().contains("duplicate_parameter");
  }
}
