package com.jcaa.events;

import static org.assertj.core.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class DomainEventContractTest {
  @ParameterizedTest
  @ValueSource(strings = {"user-created-v1.json", "account-created-v1.json", "user-deleted-v1.json"})
  void wireExamplesRoundTripAcrossIndependentServices(String file) throws Exception {
    ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
    DomainEvent event =
        mapper.readValue(Files.readString(Path.of("contracts", file)), DomainEvent.class);
    assertThat(mapper.readValue(mapper.writeValueAsString(event), DomainEvent.class))
        .isEqualTo(event);
    assertThat(event.schemaVersion()).isEqualTo(1);
    assertThat(event.toString()).doesNotContain("demo@example.test");
  }
}
