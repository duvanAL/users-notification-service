package com.jcaa.notifications;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.jcaa.notifications.infrastructure.history.*;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
class NotificationHistoryAuthorizationTest {
 static final String SECRET="dGVzdC1vbmx5LWp3dC1zZWNyZXQtd2l0aC1hdC1sZWFzdC0zMi1ieXRlcw==";
 String token(String role,Date expiration){return "Bearer "+Jwts.builder().subject("owner-1").claim("role",role).issuedAt(new Date()).expiration(expiration).signWith(Keys.hmacShaKeyFor(Base64.getDecoder().decode(SECRET))).compact();}
 @Test void memberIsAlwaysScopedAndCannotChooseAnotherOwner(){var store=mock(NotificationHistoryStore.class);var c=new NotificationHistoryController(store,SECRET);var auth=token("MEMBER",new Date(System.currentTimeMillis()+60000));c.list(auth,null,null,20,0);verify(store).list("owner-1",null,20,0);assertThatThrownBy(()->c.list(auth,"someone-else",null,20,0)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("403");}
 @Test void missingExpiredAndUnknownRoleAreRejected(){var store=mock(NotificationHistoryStore.class);var c=new NotificationHistoryController(store,SECRET);for(String auth:Arrays.asList(null,"Bearer invalid",token("ADMIN",new Date(1)),token("ROOT",new Date(System.currentTimeMillis()+60000))))assertThatThrownBy(()->c.list(auth,null,null,20,0)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("401");verifyNoInteractions(store);}
 @Test void staffMayFilterButLimitsAndStatusesAreValidated(){var store=mock(NotificationHistoryStore.class);var c=new NotificationHistoryController(store,SECRET);var auth=token("REVIEWER",new Date(System.currentTimeMillis()+60000));c.list(auth,"owner-2","SENT",20,0);verify(store).list("owner-2","SENT",20,0);assertThatThrownBy(()->c.list(auth,null,"UNKNOWN",20,0)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("400");assertThatThrownBy(()->c.list(auth,null,null,101,0)).isInstanceOf(ResponseStatusException.class).hasMessageContaining("400");}
}
