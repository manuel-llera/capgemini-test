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
class UserValidationAcceptanceIT extends AcceptanceTestSupport {
  @Test
  void rejectsInvalidNameWithoutCallingDependenciesOrPersistingUser() throws Exception {
    UserData user = withName(uniqueUser("admin"), "toolong");

    assertValidationError(user, "userName");

    assertNoExternalRequests(user);
  }

  @Test
  void rejectsInvalidEmailWithoutCallingDependenciesOrPersistingUser() throws Exception {
    UserData user = withEmail(uniqueUser("admin"), "invalid." + System.nanoTime());

    assertValidationError(user, "email");

    assertNoExternalRequests(user);
  }

  @Test
  void rejectsUnsupportedRoleWithoutCallingDependenciesOrPersistingUser() throws Exception {
    UserData user = withRole(uniqueUser("admin"), "user");

    assertValidationError(user, "role");

    assertNoExternalRequests(user);
  }

  @Test
  void rejectsDniConflictWithoutPersistingOrSendingNotification() throws Exception {
    UserData user = withDni(uniqueUser("admin"), "99999999w");

    assertValidationError(user, "dni");

    assertThat(userCountByEmail(user.email())).isZero();
    assertThat(
            mockServerRequestCount(
                "PATCH", "/check-dni", Map.of("dni", user.dni())))
        .isEqualTo(1);
    assertThat(
            mockServerRequestCount(
                "POST", "/email", Map.of("email", user.email())))
        .isZero();
  }

  @Test
  void duplicateEmailReturnsConflictAndKeepsTheOriginalUser() throws Exception {
    UserData original = uniqueUser("admin");
    ResponseEntity<JsonNode> created = createUser(original);
    assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    long originalId = created.getBody().path("id").asLong();
    assertThat(userCountByEmail(original.email())).isEqualTo(1);

    UserData duplicate = withEmail(uniqueUser("admin"), original.email());
    ResponseEntity<JsonNode> response = createUser(duplicate);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody().path("code").asInt()).isEqualTo(409);
    assertThat(response.getBody().path("message").asText())
        .isEqualTo("error validation email");
    assertThat(userCountByEmail(original.email())).isEqualTo(1);
    assertThat(getUser(originalId).getStatusCode()).isEqualTo(HttpStatus.OK);
    assertThat(
            mockServerRequestCount(
                "PATCH", "/check-dni", Map.of("dni", duplicate.dni())))
        .isZero();
    assertThat(
            mockServerRequestCount(
                "POST",
                "/email",
                Map.of("email", original.email(), "message", "usuario guardado")))
        .isEqualTo(1);
  }

  private void assertValidationError(UserData user, String field) {
    ResponseEntity<JsonNode> response = createUser(user);

    assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    assertThat(response.getBody().path("code").asInt()).isEqualTo(409);
    assertThat(response.getBody().path("message").asText())
        .isEqualTo("error validation " + field);
    assertThat(userCountByEmail(user.email())).isZero();
  }

  private void assertNoExternalRequests(UserData user)
      throws java.io.IOException, InterruptedException {
    assertThat(
            mockServerRequestCount(
                "PATCH", "/check-dni", Map.of("dni", user.dni())))
        .isZero();
    assertThat(
            mockServerRequestCount(
                "POST", "/email", Map.of("email", user.email())))
        .isZero();
    assertThat(
            mockServerRequestCount(
                "POST", "/sms", Map.of("phone", user.phone())))
        .isZero();
  }
}
