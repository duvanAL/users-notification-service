package com.jcaa.notifications.infrastructure.history;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.util.Base64;
import java.util.List;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@ConditionalOnProperty(name = "app.history.enabled", havingValue = "true")
public class NotificationHistoryController {
  private final NotificationHistoryStore history;
  private final SecretKey key;
  public NotificationHistoryController(NotificationHistoryStore history,@Value("${JWT_SECRET}") String secret) {
    this.history=history;
    this.key=Keys.hmacShaKeyFor(Base64.getDecoder().decode(secret));
  }
  @GetMapping("/api/notifications")
  public NotificationHistoryStore.Page list(@RequestHeader(value="Authorization",required=false) String authorization,
      @RequestParam(required=false) String userId,@RequestParam(required=false) String status,
      @RequestParam(defaultValue="30") int limit,@RequestParam(defaultValue="0") int offset) {
    String subject;
    String role;
    try {
      if (authorization==null || !authorization.startsWith("Bearer ")) throw new IllegalArgumentException();
      var claims=Jwts.parser().verifyWith(key).build().parseSignedClaims(authorization.substring(7)).getPayload();
      subject=claims.getSubject(); role=claims.get("role",String.class);
      if(subject==null || claims.getExpiration()==null || !List.of("ADMIN","REVIEWER","MEMBER").contains(role)) throw new IllegalArgumentException();
    } catch(RuntimeException error) { throw new ResponseStatusException(HttpStatus.UNAUTHORIZED); }
    if ("MEMBER".equals(role)) {
      if(userId!=null && !subject.equals(userId)) throw new ResponseStatusException(HttpStatus.FORBIDDEN);
      userId=subject;
    }
    if(limit<1 || limit>100 || offset<0 || offset>10000 ||
        (status!=null && !List.of("PENDING","SENT","FAILED").contains(status)))
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST);
    return history.list(userId,status,limit,offset);
  }
}
