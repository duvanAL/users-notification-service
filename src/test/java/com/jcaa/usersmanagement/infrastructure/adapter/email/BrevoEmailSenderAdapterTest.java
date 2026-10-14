package com.jcaa.usersmanagement.infrastructure.adapter.email;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.jcaa.usersmanagement.domain.exception.EmailSenderException;
import com.jcaa.usersmanagement.domain.model.EmailDestinationModel;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.retry.Retry;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class BrevoEmailSenderAdapterTest {

  private static final String API_KEY = "fake-test-api-key";
  private static final String BASE_URL = "https://api.brevo.test";

  @Test
  void shouldSendTransactionalEmailUsingBrevoRestContract() {
    final RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
    final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(requestTo(BASE_URL + "/v3/smtp/email"))
        .andExpect(method(HttpMethod.POST))
        .andExpect(header("api-key", API_KEY))
        .andExpect(content().contentType(MediaType.APPLICATION_JSON))
        .andExpect(
            content()
                .json(
                    """
                    {
                      "sender": {"email":"no-reply@example.com", "name":"Users API"},
                      "to": [{"email":"ada@example.com", "name":"Ada"}],
                      "subject":"Account updated",
                      "htmlContent":"<p>Updated</p>",
                      "headers":{"idempotencyKey":"d48e20a5-1fcb-4d67-b5cc-76daf9539b05"}
                    }
                    """))
        .andRespond(withSuccess());

    adapter(builder).send(destination(), "d48e20a5-1fcb-4d67-b5cc-76daf9539b05");

    server.verify();
  }

  @Test
  void shouldTreatBrevoDuplicateIdempotencyResponseAsAlreadyDelivered() {
    final RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
    final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(requestTo(BASE_URL + "/v3/smtp/email"))
        .andRespond(withStatus(HttpStatus.BAD_REQUEST).body("{\"code\":\"duplicate_parameter\"}"));

    adapter(builder).send(destination(), UUID.randomUUID().toString());

    server.verify();
  }

  @Test
  void shouldTranslateProviderHttpErrorsWithoutExposingResponseBody() {
    final RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
    final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    for (int attempt = 0; attempt < 3; attempt++) {
      server
          .expect(requestTo(BASE_URL + "/v3/smtp/email"))
          .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).body("sensitive provider response"));
    }

    assertThatThrownBy(() -> adapter(builder, 3, 10, 5).send(destination()))
        .isInstanceOf(EmailSenderException.class)
        .hasMessage("La notificación por correo no pudo ser enviada.")
        .hasRootCauseMessage("Brevo API request failed: HTTP 429.")
        .hasMessageNotContaining("sensitive provider response");

    server.verify();
  }

  @Test
  void shouldRetryTransientServerFailuresAndStopAfterProviderAccepts() {
    final RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
    final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(requestTo(BASE_URL + "/v3/smtp/email"))
        .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
    server.expect(requestTo(BASE_URL + "/v3/smtp/email")).andRespond(withSuccess());

    adapter(builder, 3, 10, 5).send(destination());

    server.verify();
  }

  @Test
  void shouldNotRetryPermanentClientErrors() {
    final RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
    final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(requestTo(BASE_URL + "/v3/smtp/email"))
        .andRespond(withStatus(HttpStatus.BAD_REQUEST));

    assertThatThrownBy(() -> adapter(builder, 3, 10, 5).send(destination()))
        .isInstanceOf(EmailSenderException.class)
        .hasRootCauseMessage("Brevo API request failed: HTTP 400.");

    server.verify();
  }

  @Test
  void shouldOpenCircuitAfterRepeatedTransientFailures() {
    final RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
    final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
    server
        .expect(requestTo(BASE_URL + "/v3/smtp/email"))
        .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
    server
        .expect(requestTo(BASE_URL + "/v3/smtp/email"))
        .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
    final BrevoEmailSenderAdapter adapter = adapter(builder, 1, 2, 2);

    assertThatThrownBy(() -> adapter.send(destination()))
        .isInstanceOf(EmailSenderException.class)
        .hasRootCauseMessage("Brevo API request failed: HTTP 503.");
    assertThatThrownBy(() -> adapter.send(destination()))
        .isInstanceOf(EmailSenderException.class)
        .hasRootCauseMessage("Brevo API request failed: HTTP 503.");
    assertThatThrownBy(() -> adapter.send(destination()))
        .isInstanceOf(EmailSenderException.class)
        .hasRootCauseMessage("Brevo API request failed: CallNotPermittedException.");

    server.verify();
  }

  private static BrevoEmailSenderAdapter adapter(final RestClient.Builder builder) {
    return adapter(builder, 1, 10, 5);
  }

  private static BrevoEmailSenderAdapter adapter(
      final RestClient.Builder builder,
      final int retryMaxAttempts,
      final int circuitWindowSize,
      final int circuitMinimumCalls) {
    final BrevoConfig config =
        new BrevoConfig(
            BASE_URL,
            API_KEY,
            "no-reply@example.com",
            "Users API",
            Duration.ofSeconds(2),
            Duration.ofSeconds(5));
    final RestClient client = builder.defaultHeader("api-key", API_KEY).build();
    final BrevoResilienceConfig resilienceConfig =
        new BrevoResilienceConfig(
            retryMaxAttempts,
            Duration.ofMillis(1),
            1.0,
            50,
            circuitWindowSize,
            circuitMinimumCalls,
            Duration.ofSeconds(30));
    final Retry retry = BrevoResilience.retry(resilienceConfig);
    final CircuitBreaker circuitBreaker = BrevoResilience.circuitBreaker(resilienceConfig);
    return new BrevoEmailSenderAdapter(config, client, retry, circuitBreaker);
  }

  private static EmailDestinationModel destination() {
    return new EmailDestinationModel("ada@example.com", "Ada", "Account updated", "<p>Updated</p>");
  }
}
