package com.jcaa.notifications;

import com.jcaa.usersmanagement.application.port.out.EmailSenderPort;
import com.jcaa.usersmanagement.infrastructure.adapter.email.BrevoConfig;
import com.jcaa.usersmanagement.infrastructure.adapter.email.BrevoEmailSenderAdapter;
import com.jcaa.usersmanagement.infrastructure.adapter.email.BrevoResilience;
import com.jcaa.usersmanagement.infrastructure.adapter.email.BrevoResilienceConfig;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
public class EmailConfiguration {
  @Bean
  EmailSenderPort emailSender(
      @Value("${BREVO_API_KEY:}") String key,
      @Value("${BREVO_FROM_ADDRESS:}") String address,
      @Value("${BREVO_FROM_NAME:Gestion de Usuarios}") String name,
      @Value("${BREVO_BASE_URL:https://api.brevo.com}") String url,
      @Value("${BREVO_CONNECT_TIMEOUT_MS:3000}") int connect,
      @Value("${BREVO_READ_TIMEOUT_MS:10000}") int read) {
    BrevoConfig config =
        new BrevoConfig(
            url, key, address, name, Duration.ofMillis(connect), Duration.ofMillis(read));
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    factory.setConnectTimeout(Duration.ofMillis(connect));
    factory.setReadTimeout(Duration.ofMillis(read));
    RestClient client =
        RestClient.builder()
            .baseUrl(url)
            .requestFactory(factory)
            .defaultHeader("api-key", key)
            .defaultHeader("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .defaultHeader("Accept", MediaType.APPLICATION_JSON_VALUE)
            .build();
    BrevoResilienceConfig resilience =
        new BrevoResilienceConfig(
            3, Duration.ofMillis(500), 2.0, 50, 10, 5, Duration.ofSeconds(30));
    return new BrevoEmailSenderAdapter(
        config,
        client,
        BrevoResilience.retry(resilience),
        BrevoResilience.circuitBreaker(resilience));
  }
}
