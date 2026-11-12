package com.capgemini.test.code;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import com.capgemini.test.code.support.ExternalServiceStub;
import com.capgemini.test.code.support.ExternalServiceStub.RecordedRequest;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserApiIntegrationTest {
  private static final ExternalServiceStub externalServiceStub = new ExternalServiceStub();

  @DynamicPropertySource
  static void configureExternalService(DynamicPropertyRegistry registry) {
    registry.add("external.service.url", externalServiceStub::baseUrl);
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private ObjectMapper objectMapper;

  private final List<String> createdEmails = new ArrayList<>();

  @BeforeEach
  void resetExternalServiceStub() {
    externalServiceStub.reset();
  }

  @AfterAll
  static void stopExternalServiceStub() {
    externalServiceStub.close();
  }

  @AfterEach
  void removeCreatedUsers() {
    for (String email : createdEmails) {
      jdbcTemplate.update("DELETE FROM users WHERE email = ?", email);
    }
    createdEmails.clear();
  }

  @Test
  void flywayMigratesTheDedicatedTestDatabaseAndSeedsRooms() {
    assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM rooms", Long.class)).isEqualTo(2L);
    assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Long.class)).isEqualTo(2L);
    assertThat(jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM flyway_schema_history WHERE success = TRUE", Long.class))
        .isEqualTo(2L);
    assertThat(columnType("users", "id")).isEqualTo("bigint");
    assertThat(columnType("users", "room_id")).isEqualTo("bigint");
    assertThat(columnType("rooms", "id")).isEqualTo("bigint");
  }

  @Test
  void adminCanCreateAndReadBackAUserAndExternalHttpContractIsCorrect() throws Exception {
    TestUser user = uniqueUser("admin");
    String response =
        mockMvc
            .perform(postUser(user, "admin"))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.id").isNumber())
            .andReturn()
            .getResponse()
            .getContentAsString();
    long id = objectMapper.readTree(response).get("id").asLong();

    mockMvc
        .perform(get("/users/{id}", id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id))
        .andExpect(jsonPath("$.email").value(user.email()))
        .andExpect(jsonPath("$.rol").value("admin"));

    assertThat(externalServiceStub.requests())
        .extracting(RecordedRequest::path)
        .containsExactly("/check-dni", "/email");
    assertRequest("/check-dni", "PATCH", "dni", user.dni());
    assertRequest("/email", "POST", "email", user.email(), "message", "usuario guardado");
  }

  @Test
  void superadminIsNotifiedBySmsUsingTheExpectedHttpContract() throws Exception {
    TestUser user = uniqueUser("superadmin");

    mockMvc
        .perform(postUser(user, "superadmin"))
        .andExpect(status().isCreated())
        .andExpect(header().exists("Location"));

    assertThat(externalServiceStub.requests())
        .extracting(RecordedRequest::path)
        .containsExactly("/check-dni", "/sms");
    assertRequest("/sms", "POST", "phone", user.phone(), "message", "usuario guardado");
  }

  @Test
  void roomOneCanContainMultipleUsersAndGetDoesNotExposeUsersFromAnotherRoom()
      throws Exception {
    TestUser first = uniqueUser("roomonefirst");
    TestUser second = uniqueUser("roomonesecond");
    long firstId = createUser(first, "admin");
    long secondId = createUser(second, "admin");

    assertThat(firstId).isNotEqualTo(secondId);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE room_id = 1 AND email IN (?, ?)",
                Long.class,
                first.email(),
                second.email()))
        .isEqualTo(2L);
    mockMvc.perform(get("/users/{id}", firstId)).andExpect(status().isOk());
    mockMvc.perform(get("/users/{id}", secondId)).andExpect(status().isOk());

    TestUser otherRoomUser = uniqueUser("roomtwo");
    createdEmails.add(otherRoomUser.email());
    Long otherRoomId =
        jdbcTemplate.queryForObject(
            """
            INSERT INTO users (name, email, dni, phone, role, room_id)
            VALUES (?, ?, ?, ?, ?, 2)
            RETURNING id
            """,
            Long.class,
            otherRoomUser.name(),
            otherRoomUser.email(),
            otherRoomUser.dni(),
            otherRoomUser.phone(),
            "admin");

    mockMvc
        .perform(get("/users/{id}", otherRoomId))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.code").value(404));
  }

  @Test
  void duplicateEmailReturnsConflictWithoutCallingExternalDniService() throws Exception {
    String email = "juan@example.com";
    TestUser duplicate = new TestUser("pablo", email, uniquePhone(), "12345678Z");

    mockMvc
        .perform(postUser(duplicate, "admin"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value(409))
        .andExpect(jsonPath("$.message").value("error validation email"));

    assertThat(externalServiceStub.requests()).isEmpty();
  }

  @Test
  void externalDniConflictReturnsDocumentedValidationErrorAndDoesNotPersist() throws Exception {
    TestUser generated = uniqueUser("invaliddni");
    TestUser user =
        new TestUser(generated.name(), generated.email(), generated.phone(), "99999999w");

    mockMvc
        .perform(postUser(user, "admin"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value(409))
        .andExpect(jsonPath("$.message").value("error validation dni"));

    assertThat(userCountByEmail(user.email())).isZero();
    assertThat(externalServiceStub.requests())
        .extracting(RecordedRequest::path)
        .containsExactly("/check-dni");
    assertRequest("/check-dni", "PATCH", "dni", user.dni());
  }

  @Test
  void invalidNameEmailAndRoleReturnTheirValidationConflictsBeforeExternalCalls()
      throws Exception {
    TestUser longName = new TestUser("toolong", uniqueEmail("longname"), uniquePhone(), uniqueDni());
    TestUser invalidEmail = new TestUser("pablo", "invalid.example.com", uniquePhone(), uniqueDni());
    TestUser invalidRole = uniqueUser("invalidrole");

    mockMvc
        .perform(postUser(longName, "admin"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("error validation userName"));
    mockMvc
        .perform(postUser(invalidEmail, "admin"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("error validation email"));
    mockMvc
        .perform(postUser(invalidRole, "user"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("error validation role"));

    assertThat(externalServiceStub.requests()).isEmpty();
    assertThat(userCountByEmail(longName.email())).isZero();
    assertThat(userCountByEmail(invalidEmail.email())).isZero();
    assertThat(userCountByEmail(invalidRole.email())).isZero();
  }

  @Test
  void databaseRejectsDuplicateDniEvenWhenExternalValidationSucceeds() throws Exception {
    TestUser first = uniqueUser("first");
    TestUser duplicateDni = new TestUser("other", uniqueEmail("other"), uniquePhone(), first.dni());
    createdEmails.add(duplicateDni.email());

    mockMvc.perform(postUser(first, "admin")).andExpect(status().isCreated());

    mockMvc
        .perform(postUser(duplicateDni, "admin"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.message").value("error validation dni"));

    assertThat(userCountByEmail(first.email())).isEqualTo(1);
    assertThat(userCountByEmail(duplicateDni.email())).isZero();
    assertThat(externalServiceStub.requests())
        .extracting(RecordedRequest::path)
        .containsExactly("/check-dni", "/email", "/check-dni");
  }

  @Test
  void notificationFailureReturnsCreatedIdWhenUserWasPersisted() throws Exception {
    TestUser user = uniqueUser("emailfailure");
    externalServiceStub.setNotificationStatus(500);

    String response =
    mockMvc
        .perform(postUser(user, "admin"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").isNumber())
        .andReturn()
        .getResponse()
        .getContentAsString();
    long id = objectMapper.readTree(response).get("id").asLong();

    assertThat(userCountByEmail(user.email())).isEqualTo(1);
    mockMvc
        .perform(get("/users/{id}", id))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(id))
        .andExpect(jsonPath("$.email").value(user.email()));
    assertThat(externalServiceStub.requests())
        .extracting(RecordedRequest::path)
        .containsExactly("/check-dni", "/email");
  }

  private org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder postUser(
      TestUser user, String role) {
    if (user.email().startsWith("qa.")) {
      createdEmails.add(user.email());
    }
    String body =
        """
        {"name":"%s","email":"%s","phone":"%s","rol":"%s","dni":"%s"}
        """
            .formatted(user.name(), user.email(), user.phone(), role, user.dni());
    return post("/users").contentType(MediaType.APPLICATION_JSON).content(body);
  }

  private TestUser uniqueUser(String suffix) {
    return new TestUser("pablo", uniqueEmail(suffix), uniquePhone(), uniqueDni());
  }

  private String uniqueEmail(String suffix) {
    return "qa." + suffix + "." + UUID.randomUUID().toString().replace("-", "") + "@example.com";
  }

  private String uniquePhone() {
    return Long.toString(ThreadLocalRandom.current().nextLong(1_000_000_000L, 9_999_999_999L));
  }

  private String uniqueDni() {
    return UUID.randomUUID().toString().replace("-", "").substring(0, 8) + "A";
  }

  private long createUser(TestUser user, String role) throws Exception {
    String response =
        mockMvc
            .perform(postUser(user, role))
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();
    return objectMapper.readTree(response).get("id").asLong();
  }

  private long userCountByEmail(String email) {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM users WHERE email = ?", Long.class, email);
  }

  private String columnType(String table, String column) {
    return jdbcTemplate.queryForObject(
        """
        SELECT data_type FROM information_schema.columns
        WHERE table_schema = 'public' AND table_name = ? AND column_name = ?
        """,
        String.class,
        table,
        column);
  }

  private void assertRequest(String path, String method, String... expectedFields)
      throws Exception {
    RecordedRequest request =
        externalServiceStub.requests().stream()
            .filter(candidate -> candidate.path().equals(path))
            .findFirst()
            .orElseThrow();
    assertThat(request.method()).isEqualTo(method);
    JsonNode json = objectMapper.readTree(request.body());
    for (int index = 0; index < expectedFields.length; index += 2) {
      assertThat(json.get(expectedFields[index]).asText()).isEqualTo(expectedFields[index + 1]);
    }
  }

  private record TestUser(String name, String email, String phone, String dni) {}
}
