package com.jcaa.usersmanagement.infrastructure.adapter.email;

import java.util.List;
import java.util.Map;

public record BrevoEmailRequest(
    Sender sender,
    List<Recipient> to,
    String subject,
    String htmlContent,
    Map<String, String> headers) {

  public record Sender(String email, String name) {}

  public record Recipient(String email, String name) {}
}
