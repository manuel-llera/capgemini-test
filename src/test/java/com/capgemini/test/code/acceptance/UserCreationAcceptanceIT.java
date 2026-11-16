package com.capgemini.test.code.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class UserCreationAcceptanceIT extends AcceptanceTestSupport {
  @Test
  void adminCanCreatePersistAndReadBackAUserWithEmailNotification() throws Exception {
    UserData user = uniqueUser("admin");

    ResponseEntity<JsonNode> created = createUser(user);

    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    assertThat(created.getHeaders().getLocation()).isNotNull();
    long id = created.getBody().path("id").asLong();
    assertThat(id).isPositive();
    assertThat(created.getHeaders().getLocation().getPath()).endsWith("/users/" + id);
    assertThat(userCountByEmail(user.email())).isEqualTo(1);

    ResponseEntity<JsonNode> retrieved = getUser(id);

    assertThat(retrieved.getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(retrieved.getBody().path("id").asLong()).isEqualTo(id);
    assertThat(retrieved.getBody().path("email").asText()).isEqualTo(user.email());
    assertThat(retrieved.getBody().path("rol").asText()).isEqualTo("admin");
    assertThat(
            mockServerRequestCount(
                "PATCH", "/check-dni", Map.of("dni", user.dni())))
        .isEqualTo(1);
    assertThat(
            mockServerRequestCount(
                "POST",
                "/email",
                Map.of("email", user.email(), "message", "usuario guardado")))
        .isEqualTo(1);
  }
}
