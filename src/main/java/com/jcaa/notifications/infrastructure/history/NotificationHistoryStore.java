package com.jcaa.notifications.infrastructure.history;

import com.jcaa.notifications.application.port.out.NotificationHistoryPort;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.history.enabled", havingValue = "true")
public class NotificationHistoryStore implements NotificationHistoryPort {
  private final String url;
  private final String username;
  private final String password;
  public NotificationHistoryStore(
      @Value("${NOTIFICATIONS_DATABASE_URL}") String url,
      @Value("${NOTIFICATIONS_DATABASE_USERNAME}") String username,
      @Value("${NOTIFICATIONS_DATABASE_PASSWORD}") String password) {
    if (!url.startsWith("jdbc:postgresql:")) throw new IllegalArgumentException("PostgreSQL required");
    this.url = url;
    this.username = username;
    this.password = password;
  }
  private Connection connect() throws SQLException {
    return DriverManager.getConnection(url, username, password);
  }
  @Override
  public void record(UUID eventId, String userId, String eventName, String status, String failureCode) {
    try (var connection = connect(); var statement = connection.prepareStatement(
        "INSERT INTO notifications.delivery_history (event_id,user_id,event_name,status,failure_code)"
        + " VALUES (?,?,?,?,?) ON CONFLICT(event_id) DO UPDATE SET status=EXCLUDED.status,"
        + " failure_code=EXCLUDED.failure_code,updated_at=CURRENT_TIMESTAMP"
        + " WHERE notifications.delivery_history.status <> 'SENT'")) {
      statement.setObject(1, eventId);
      statement.setString(2, userId);
      statement.setString(3, eventName);
      statement.setString(4, status);
      statement.setString(5, failureCode);
      statement.executeUpdate();
    } catch (SQLException error) { throw new IllegalStateException("Notification history unavailable"); }
  }
  public record Entry(UUID id, String userId, String eventName, String status, String failureCode, String createdAt, String updatedAt) {}
  public record Page(List<Entry> items, long total) {}
  public Page list(String userId, String status, int limit, int offset) {
    String filter = " WHERE (?::text IS NULL OR user_id=?) AND (?::text IS NULL OR status=?)";
    try (var connection = connect(); var query = connection.prepareStatement(
        "SELECT * FROM notifications.delivery_history" + filter
        + " ORDER BY created_at DESC,event_id LIMIT ? OFFSET ?");
        var count = connection.prepareStatement("SELECT count(*) FROM notifications.delivery_history" + filter)) {
      connection.setReadOnly(true);
      connection.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
      connection.setAutoCommit(false);
      for (var statement : List.of(query, count)) {
        statement.setString(1,userId); statement.setString(2,userId);
        statement.setString(3,status); statement.setString(4,status);
      }
      query.setInt(5,limit); query.setInt(6,offset);
      List<Entry> entries = new ArrayList<>();
      try (var rows = query.executeQuery()) {
        while (rows.next()) entries.add(new Entry(rows.getObject("event_id",UUID.class), rows.getString("user_id"),
            rows.getString("event_name"),rows.getString("status"),rows.getString("failure_code"),
            rows.getTimestamp("created_at").toInstant().toString(),rows.getTimestamp("updated_at").toInstant().toString()));
      }
      try (var rows = count.executeQuery()) { rows.next(); return new Page(entries,rows.getLong(1)); }
    } catch (SQLException error) { throw new IllegalStateException("Notification history unavailable"); }
  }
}
