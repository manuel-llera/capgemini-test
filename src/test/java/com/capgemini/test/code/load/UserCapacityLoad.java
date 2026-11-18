package com.capgemini.test.code.load;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.LongAdder;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class UserCapacityLoad {
  private static final int DEFAULT_FLOW_COUNT = 100;
  private static final int MAX_FLOW_COUNT = 50_000;
  private static final int REQUEST_LOG_BATCH_SIZE = 1_000;
  private static final String HIGH_VOLUME_CONFIRMATION = "users_test";
  private static final URI MOCKSERVER_CLEAR_URI =
      URI.create("http://localhost:1080/mockserver/clear?type=LOG");
  private static final URI MOCKSERVER_RETRIEVE_URI =
      URI.create("http://localhost:1080/mockserver/retrieve?type=REQUESTS");
  private static final HttpClient HTTP_CLIENT =
      HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
  private static final int[] LATENCY_BUCKETS_MS =
      {1, 2, 5, 10, 20, 50, 100, 200, 500, 1_000, 2_000, 5_000, 10_000};

  @LocalServerPort private int port;
  @Autowired private JdbcTemplate jdbcTemplate;
  @Autowired private ObjectMapper objectMapper;

  @Test
  void completesTheConfiguredNumberOfCreateAndReadFlows() throws Exception {
    assertThat(jdbcTemplate.queryForObject("SELECT current_database()", String.class))
        .isEqualTo("users_test");

    int flowLimit = configuredFlowCount();
    String runId = UUID.randomUUID().toString().replace("-", "");
    String emailPrefix = "capacity." + runId;
    String dniRunPrefix = runId.substring(0, 8).toUpperCase(Locale.ROOT);
    long phoneBase = phoneBase(runId);
    URI usersUri = URI.create("http://localhost:" + port + "/users");
    LoadMetrics metrics = new LoadMetrics();
    int nextSequence = 1;
    long runStartedAt = System.nanoTime();

    System.out.println(
        "Capacity probe: target="
            + flowLimit
            + " create/read flows ("
            + (flowLimit * 2L)
            + " API requests), database=users_test, baseUrl="
            + usersUri);
    System.out.println(
        "Emergency cleanup SQL: DELETE FROM users WHERE email LIKE '"
            + emailPrefix
            + ".%@example.com';");

    try {
      verifyReadiness(usersUri);
      clearMockServerRequestLog();

      List<Stage> ramp =
          List.of(
              new Stage(1, 100),
              new Stage(5, 900),
              new Stage(10, 9_000),
              new Stage(20, 40_000));
      for (Stage stage : ramp) {
        if (nextSequence > flowLimit) {
          break;
        }
        int stageFlows = Math.min(stage.flows(), flowLimit - nextSequence + 1);
        runStage(
            stage.workers(),
            stageFlows,
            nextSequence,
            emailPrefix,
            dniRunPrefix,
            phoneBase,
            usersUri,
            metrics);
        nextSequence += stageFlows;
        logProgress(
            stage.workers(),
            nextSequence - 1,
            flowLimit,
            runStartedAt,
            metrics);
      }

      while (nextSequence <= flowLimit) {
        int stageFlows = Math.min(50_000, flowLimit - nextSequence + 1);
        runStage(
            20,
            stageFlows,
            nextSequence,
            emailPrefix,
            dniRunPrefix,
            phoneBase,
            usersUri,
            metrics);
        nextSequence += stageFlows;
        logProgress(20, nextSequence - 1, flowLimit, runStartedAt, metrics);
      }

      assertThat(metrics.completedFlows.sum()).isEqualTo(flowLimit);
      assertThat(
              jdbcTemplate.queryForObject(
                  "SELECT COUNT(*) FROM users WHERE email LIKE ?",
                  Long.class,
                  emailPrefix + ".%@example.com"))
          .isEqualTo((long) flowLimit);

      double elapsedSeconds = (System.nanoTime() - runStartedAt) / 1_000_000_000.0;
      System.out.printf(
          Locale.ROOT,
          "Completed %d flows: API requests=%d, elapsed=%.1fs, flows/s=%.2f%n",
          flowLimit,
          metrics.apiRequests.sum(),
          elapsedSeconds,
          flowLimit / elapsedSeconds);
      metrics.logLatencySummary();
    } finally {
      int deleted =
          jdbcTemplate.update(
              "DELETE FROM users WHERE email LIKE ?", emailPrefix + ".%@example.com");
      System.out.println("Removed " + deleted + " records created by this run.");
      assertThat(
              jdbcTemplate.queryForObject(
                  "SELECT COUNT(*) FROM users WHERE email LIKE ?",
                  Long.class,
                  emailPrefix + ".%@example.com"))
          .isZero();
    }
  }

  private int configuredFlowCount() {
    int flowCount = Integer.getInteger("capacity.test.flows", DEFAULT_FLOW_COUNT);
    if (flowCount < 1 || flowCount > MAX_FLOW_COUNT) {
      throw new IllegalArgumentException(
          "capacity.test.flows must be between 1 and " + MAX_FLOW_COUNT);
    }
    if (flowCount > 1_000
        && !HIGH_VOLUME_CONFIRMATION.equals(System.getProperty("capacity.test.confirm"))) {
      throw new IllegalArgumentException(
          "For more than 1,000 flows, pass -Dcapacity.test.confirm="
              + HIGH_VOLUME_CONFIRMATION
              + " to confirm writes to users_test.");
    }
    return flowCount;
  }

  private void verifyReadiness(URI usersUri) throws IOException, InterruptedException {
    HttpResponse<String> response = sendGet(usersUri.resolve("/users/1"));
    assertThat(response.statusCode())
        .as("GET /users/1 readiness check")
        .isEqualTo(200);
  }

  private void runStage(
      int workers,
      int flowCount,
      int firstSequence,
      String emailPrefix,
      String dniRunPrefix,
      long phoneBase,
      URI usersUri,
      LoadMetrics metrics)
      throws Exception {
    ExecutorService executor = Executors.newFixedThreadPool(workers);
    try {
      int offset = 0;
      while (offset < flowCount) {
        int batchCount = Math.min(REQUEST_LOG_BATCH_SIZE, flowCount - offset);
        List<LoadUser> users =
            createUsers(
                firstSequence + offset,
                batchCount,
                emailPrefix,
                dniRunPrefix,
                phoneBase);
        clearMockServerRequestLog();
        executeBatch(executor, workers, usersUri, users, metrics);
        verifyMockServerRequests(users);
        offset += batchCount;
      }
    } finally {
      executor.shutdownNow();
      if (!executor.awaitTermination(15, TimeUnit.SECONDS)) {
        throw new IllegalStateException("Capacity-test workers did not stop after the batch.");
      }
    }
  }

  private List<LoadUser> createUsers(
      int firstSequence,
      int count,
      String emailPrefix,
      String dniRunPrefix,
      long phoneBase) {
    List<LoadUser> users = new ArrayList<>(count);
    for (int sequence = firstSequence; sequence < firstSequence + count; sequence++) {
      users.add(
          new LoadUser(
              "pablo",
              emailPrefix + "." + sequence + "@example.com",
              Long.toString(phoneBase + sequence),
              "admin",
              dniRunPrefix + Integer.toString(sequence, 36).toUpperCase(Locale.ROOT)));
    }
    return users;
  }

  private void executeBatch(
      ExecutorService executor,
      int workers,
      URI usersUri,
      List<LoadUser> users,
      LoadMetrics metrics)
      throws InterruptedException, ExecutionException {
    List<Future<?>> tasks = new ArrayList<>(workers);
    for (int worker = 0; worker < workers; worker++) {
      int firstIndex = worker;
      tasks.add(
          executor.submit(
              () -> {
                for (int index = firstIndex; index < users.size(); index += workers) {
                  executeFlow(usersUri, users.get(index), metrics);
                }
              }));
    }
    for (Future<?> task : tasks) {
      task.get();
    }
  }

  private void executeFlow(URI usersUri, LoadUser user, LoadMetrics metrics) {
    try {
      HttpResponse<String> created = sendCreate(usersUri, user, metrics);
      if (created.statusCode() != 201) {
        throw new IllegalStateException(
            "POST /users returned HTTP " + created.statusCode() + ": " + created.body());
      }

      JsonNode createdBody = objectMapper.readTree(created.body());
      JsonNode idNode = createdBody.path("id");
      if (!idNode.canConvertToLong() || idNode.asLong() <= 0) {
        throw new IllegalStateException("POST /users did not return a positive user ID.");
      }

      HttpResponse<String> retrieved =
          sendGet(usersUri.resolve("/users/" + idNode.asLong()), metrics);
      if (retrieved.statusCode() != 200) {
        throw new IllegalStateException(
            "GET /users/" + idNode.asLong() + " returned HTTP " + retrieved.statusCode());
      }

      JsonNode retrievedBody = objectMapper.readTree(retrieved.body());
      if (retrievedBody.path("id").asLong() != idNode.asLong()
          || !user.email().equals(retrievedBody.path("email").asText())) {
        throw new IllegalStateException("GET returned a different user than the one created.");
      }
      metrics.completedFlows.increment();
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Create/read flow failed for " + user.email(), exception);
    } catch (IOException exception) {
      throw new IllegalStateException("Create/read flow failed for " + user.email(), exception);
    }
  }

  private HttpResponse<String> sendCreate(URI usersUri, LoadUser user, LoadMetrics metrics)
      throws IOException, InterruptedException {
    HttpRequest request =
        HttpRequest.newBuilder(usersUri)
            .timeout(Duration.ofSeconds(10))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(user)))
            .build();
    long startedAt = System.nanoTime();
    try {
      return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    } finally {
      metrics.recordApiRequest(true, System.nanoTime() - startedAt);
    }
  }

  private HttpResponse<String> sendGet(URI uri) throws IOException, InterruptedException {
    return sendGet(uri, null);
  }

  private HttpResponse<String> sendGet(URI uri, LoadMetrics metrics)
      throws IOException, InterruptedException {
    HttpRequest request =
        HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10)).GET().build();
    long startedAt = System.nanoTime();
    try {
      return HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());
    } finally {
      if (metrics != null) {
        metrics.recordApiRequest(false, System.nanoTime() - startedAt);
      }
    }
  }

  private void verifyMockServerRequests(List<LoadUser> users)
      throws IOException, InterruptedException {
    HttpResponse<String> response =
        HTTP_CLIENT.send(
            HttpRequest.newBuilder(MOCKSERVER_RETRIEVE_URI)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString("{}"))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(200);

    JsonNode requests = objectMapper.readTree(response.body());
    assertThat(requests.isArray()).isTrue();
    assertThat(requests.size()).isEqualTo(users.size() * 2);

    Set<String> expectedDnis = new HashSet<>();
    Set<String> expectedEmails = new HashSet<>();
    for (LoadUser user : users) {
      expectedDnis.add(user.dni());
      expectedEmails.add(user.email());
    }

    Set<String> receivedDnis = new HashSet<>();
    Set<String> receivedEmails = new HashSet<>();
    for (JsonNode request : requests) {
      String method = request.path("method").asText();
      String path = request.path("path").asText();
      JsonNode body = request.path("body").path("json");
      if ("PATCH".equals(method) && "/check-dni".equals(path)) {
        receivedDnis.add(body.path("dni").asText());
      } else if ("POST".equals(method) && "/email".equals(path)) {
        assertThat(body.path("message").asText()).isEqualTo("usuario guardado");
        receivedEmails.add(body.path("email").asText());
      } else {
        throw new IllegalStateException(
            "Unexpected MockServer request in capacity batch: " + method + " " + path);
      }
    }

    assertThat(receivedDnis).containsExactlyInAnyOrderElementsOf(expectedDnis);
    assertThat(receivedEmails).containsExactlyInAnyOrderElementsOf(expectedEmails);
  }

  private void clearMockServerRequestLog() throws IOException, InterruptedException {
    HttpResponse<String> response =
        HTTP_CLIENT.send(
            HttpRequest.newBuilder(MOCKSERVER_CLEAR_URI)
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString("{}"))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertThat(response.statusCode()).isEqualTo(200);
  }

  private void logProgress(
      int workers, int completed, int target, long runStartedAt, LoadMetrics metrics) {
    double elapsedSeconds = (System.nanoTime() - runStartedAt) / 1_000_000_000.0;
    System.out.printf(
        Locale.ROOT,
        "Progress: %d/%d flows, workers=%d, elapsed=%.1fs, avg flows/s=%.2f%n",
        completed,
        target,
        workers,
        elapsedSeconds,
        completed / elapsedSeconds);
    metrics.logLatencySummary();
  }

  private long phoneBase(String runId) {
    long randomPart = Long.parseLong(runId.substring(0, 15), 16);
    return 100_000_000_000_000L + randomPart % 800_000_000_000_000L;
  }

  private record LoadUser(String name, String email, String phone, String rol, String dni) {}

  private record Stage(int workers, int flows) {}

  private static final class LoadMetrics {
    private final LongAdder completedFlows = new LongAdder();
    private final LongAdder apiRequests = new LongAdder();
    private final LatencyHistogram createLatencies = new LatencyHistogram();
    private final LatencyHistogram readLatencies = new LatencyHistogram();

    private void recordApiRequest(boolean create, long latencyNanos) {
      apiRequests.increment();
      (create ? createLatencies : readLatencies).record(latencyNanos);
    }

    private void logLatencySummary() {
      System.out.println(
          "Approximate latency: POST p50/p95/p99="
              + createLatencies.percentile(0.50)
              + "/"
              + createLatencies.percentile(0.95)
              + "/"
              + createLatencies.percentile(0.99)
              + ", GET p50/p95/p99="
              + readLatencies.percentile(0.50)
              + "/"
              + readLatencies.percentile(0.95)
              + "/"
              + readLatencies.percentile(0.99));
    }
  }

  private static final class LatencyHistogram {
    private final LongAdder[] buckets = new LongAdder[LATENCY_BUCKETS_MS.length + 1];
    private final LongAdder total = new LongAdder();

    private LatencyHistogram() {
      for (int index = 0; index < buckets.length; index++) {
        buckets[index] = new LongAdder();
      }
    }

    private void record(long latencyNanos) {
      long latencyMillis = Math.max(1, latencyNanos / 1_000_000);
      int bucket = 0;
      while (bucket < LATENCY_BUCKETS_MS.length
          && latencyMillis > LATENCY_BUCKETS_MS[bucket]) {
        bucket++;
      }
      buckets[bucket].increment();
      total.increment();
    }

    private String percentile(double fraction) {
      long sampleCount = total.sum();
      if (sampleCount == 0) {
        return "n/a";
      }
      long target = (long) Math.ceil(sampleCount * fraction);
      long seen = 0;
      for (int index = 0; index < buckets.length; index++) {
        seen += buckets[index].sum();
        if (seen >= target) {
          return index == LATENCY_BUCKETS_MS.length
              ? ">" + LATENCY_BUCKETS_MS[LATENCY_BUCKETS_MS.length - 1] + "ms"
              : "<=" + LATENCY_BUCKETS_MS[index] + "ms";
        }
      }
      throw new IllegalStateException("Latency histogram did not contain all recorded samples.");
    }
  }
}
