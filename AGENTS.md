# Development
Java 17, Spring Boot 3.3.5 and Maven. Run ./mvnw -B clean verify before publishing.
Use Kafka/Aiven, not RabbitMQ. Keep the versioned contracts synchronized across all three services.
Secrets come from environment variables. Never log event payloads, authorization headers or provider responses.
Production requires SASL_SSL with hostname verification. PLAINTEXT is allowed only under the explicit local profile for isolated tests.
The image is deployable independently; no runtime dependency on another repository's source files.
