package com.capgemini.test.code.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.StreamSupport;

import org.junit.jupiter.api.AfterEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

abstract class AcceptanceTestSupport {
  private static final URI MOCKSERVER_RETRIEVE_URI =
      URI.create("http://localhost:1080/mockserver/retrieve?type=REQUESTS");
  private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();

  @LocalServerPort private int port;
  @Autowired private TestRestTemplate http;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private ObjectMapper objectMapper;

  private final Set<String> emailsCreatedByTest = new HashSet<>();

  protected UserData uniqueUser(String role) {
    String unique = UUID.randomUUID().toString().replace("-", "");
    return new UserData(
        "pablo",
        "acceptance." + unique + "@example.com",
        "6" + unique.substring(0, 8),
        role,
        unique.substring(0, 8).toUpperCase() + "A");
  }

  protected UserData withName(UserData user, String name) {
    return new UserData(name, user.email(), user.phone(), user.rol(), user.dni());
  }

  protected UserData withEmail(UserData user, String email) {
    return new UserData(user.name(), email, user.phone(), user.rol(), user.dni());
  }

  protected UserData withRole(UserData user, String role) {
    return new UserData(user.name(), user.email(), user.phone(), role, user.dni());
  }

  protected UserData withDni(UserData user, String dni) {
    return new UserData(user.name(), user.email(), user.phone(), user.rol(), dni);
  }

  protected ResponseEntity<JsonNode> createUser(UserData user) {
    emailsCreatedByTest.add(user.email());
    HttpHeaders headers = new HttpHeaders();
    headers.setContentType(MediaType.APPLICATION_JSON);
    return http.exchange(
        baseUrl() + "/users",
        HttpMethod.POST,
        new HttpEntity<>(user, headers),
        JsonNode.class);
  }

  protected ResponseEntity<JsonNode> getUser(long userId) {
    return http.getForEntity(baseUrl() + "/users/{id}", JsonNode.class, userId);
  }

  protected long userCountByEmail(String email) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM users WHERE email = ?", Long.class, email);
  }

  protected long mockServerRequestCount(String method, String path, Map<String, String> bodyFields)
      throws IOException, InterruptedException {
    HttpRequest request =
        HttpRequest.newBuilder(MOCKSERVER_RETRIEVE_URI)
            .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .PUT(HttpRequest.BodyPublishers.ofString("{}"))
            .build();
    HttpResponse<String> response =
        HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

    assertThat(response.statusCode()).isEqualTo(200);
    JsonNode requests = objectMapper.readTree(response.body());
    assertThat(requests.isArray()).isTrue();

    return StreamSupport.stream(requests.spliterator(), false)
        .filter(
            recorded ->
                method.equals(recorded.path("method").asText())
                    && path.equals(recorded.path("path").asText())
                    && bodyFields.entrySet().stream()
                        .allMatch(
                            field ->
                                field.getValue()
                                    .equals(
                                        recorded
                                            .path("body")
                                            .path("json")
                                            .path(field.getKey())
                                            .asText())))
        .count();
  }

  @AfterEach
  void removeAcceptanceUsers() {
    emailsCreatedByTest.forEach(
        email -> jdbcTemplate.update("DELETE FROM users WHERE email = ?", email));
    emailsCreatedByTest.clear();
  }

  private String baseUrl() {
    return "http://localhost:" + port;
  }

  protected record UserData(String name, String email, String phone, String rol, String dni) {}
}
