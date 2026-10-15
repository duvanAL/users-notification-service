package com.jcaa.notifications;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcaa.events.DomainEvent;
import com.jcaa.notifications.application.port.in.ProcessNotificationEventUseCase;
import com.jcaa.notifications.infrastructure.entrypoint.kafka.NotificationEventRequestMapper;
import com.jcaa.usersmanagement.domain.exception.InvalidUserEmailException;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/** Real application beans + real HTTP to a loopback Brevo contract stand-in; no live credentials. */
@SpringBootTest(properties = {"app.kafka.enabled=false", "BREVO_API_KEY=fake-http-test-key",
    "BREVO_FROM_ADDRESS=sender@example.test"})
class NotificationFlowHttpTest {
  private static final List<String> REQUESTS = new CopyOnWriteArrayList<>();
  private static final HttpServer PROVIDER;
  static {
    try {
      PROVIDER = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
      PROVIDER.createContext("/v3/smtp/email", exchange -> {
        if (!"POST".equals(exchange.getRequestMethod())
            || !"fake-http-test-key".equals(exchange.getRequestHeaders().getFirst("api-key"))) {
          exchange.sendResponseHeaders(400, -1);
        } else {
          REQUESTS.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          exchange.sendResponseHeaders(201, -1);
        }
        exchange.close();
      });
      PROVIDER.start();
    } catch (java.io.IOException exception) {
      throw new ExceptionInInitializerError(exception);
    }
  }

  @DynamicPropertySource
  static void providerUrl(DynamicPropertyRegistry properties) {
    properties.add("BREVO_BASE_URL", () -> "http://127.0.0.1:" + PROVIDER.getAddress().getPort());
  }

  @Autowired ProcessNotificationEventUseCase useCase;
  @Autowired NotificationEventRequestMapper entrypoint;
  @Autowired ObjectMapper json;

  @BeforeEach void resetRequests() { REQUESTS.clear(); }
  @AfterAll static void stopProvider() { PROVIDER.stop(0); }

  @Test
  void bothPublishersReachTheProviderThroughTheActualApplicationBeans() throws Exception {
    for (String name : new String[] {"user.created", "user.updated", "account.created"}) {
      DomainEvent event = DomainEvent.create(name, Map.of("userId", "u-1", "name", "<Demo>",
          "email", " DEMO@EXAMPLE.TEST ", "accountId", "00000000-0000-0000-0000-000000000001",
          "accountType", "SAVINGS"));
      var result = useCase.process(entrypoint.toCommand(json.writeValueAsString(event)));
      assertThat(result.status().name()).isEqualTo("SENT");
      JsonNode request = json.readTree(REQUESTS.get(REQUESTS.size() - 1));
      assertThat(request.path("headers").path("idempotencyKey").asText())
          .isEqualTo(event.eventId().toString());
      assertThat(request.path("to").get(0).path("email").asText()).isEqualTo("demo@example.test");
      assertThat(request.path("htmlContent").asText()).contains("&lt;Demo&gt;");
    }
    assertThat(REQUESTS).hasSize(3);
  }

  @Test
  void deletedAndInvalidEmailsNeverReachHttpProvider() throws Exception {
    var deleted = DomainEvent.create("user.deleted", Map.of("userId", "u-1"));
    assertThat(useCase.process(entrypoint.toCommand(json.writeValueAsString(deleted))).status().name())
        .isEqualTo("IGNORED");
    var invalid = DomainEvent.create("user.created",
        Map.of("userId", "u-1", "name", "Demo", "email", "private-invalid-value"));
    assertThatThrownBy(() -> useCase.process(entrypoint.toCommand(json.writeValueAsString(invalid))))
        .isInstanceOf(InvalidUserEmailException.class);
    assertThat(REQUESTS).isEmpty();
  }
}
