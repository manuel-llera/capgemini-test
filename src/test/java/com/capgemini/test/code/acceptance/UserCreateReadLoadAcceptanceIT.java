package com.capgemini.test.code.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class UserCreateReadLoadAcceptanceIT extends AcceptanceTestSupport {
  private static final Logger LOGGER =
      LoggerFactory.getLogger(UserCreateReadLoadAcceptanceIT.class);
  private static final int ITERATIONS = 10;
  private static final long PAUSE_MILLIS = 1_000L;

  @Test
  void sequentialCreateAndReadFlowsCompleteAndReportLatency() throws InterruptedException {
    List<Long> createLatencies = new ArrayList<>();
    List<Long> readLatencies = new ArrayList<>();
    List<Long> flowLatencies = new ArrayList<>();
    long runStartedAt = System.nanoTime();

    for (int iteration = 0; iteration < ITERATIONS; iteration++) {
      UserData user = uniqueUser("admin");

      long createStartedAt = System.nanoTime();
      ResponseEntity<JsonNode> created = createUser(user);
      createLatencies.add(System.nanoTime() - createStartedAt);

      assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
      assertThat(created.getBody()).isNotNull();
      long userId = created.getBody().path("id").asLong();
      assertThat(userId).isPositive();

      long readStartedAt = System.nanoTime();
      ResponseEntity<JsonNode> retrieved = getUser(userId);
      readLatencies.add(System.nanoTime() - readStartedAt);
      flowLatencies.add(System.nanoTime() - createStartedAt);

      assertThat(retrieved.getStatusCode()).isEqualTo(HttpStatus.OK);
      assertThat(retrieved.getBody()).isNotNull();
      assertThat(retrieved.getBody().path("id").asLong()).isEqualTo(userId);
      assertThat(retrieved.getBody().path("email").asText()).isEqualTo(user.email());
      assertThat(userCountByEmail(user.email())).isEqualTo(1);

      if (iteration + 1 < ITERATIONS) {
        Thread.sleep(PAUSE_MILLIS);
      }
    }

    double elapsedSeconds = (System.nanoTime() - runStartedAt) / 1_000_000_000.0;
    LOGGER.info(
        "Sequential create/read load: flows={}, failures=0, elapsed={}s, flows/s={}, "
            + "POST p50/p95/p99={}ms/{}ms/{}ms, GET p50/p95/p99={}ms/{}ms/{}ms, "
            + "flow p50/p95/p99={}ms/{}ms/{}ms",
        ITERATIONS,
        Math.round(elapsedSeconds * 10.0) / 10.0,
        Math.round(ITERATIONS / elapsedSeconds * 100.0) / 100.0,
        percentileMillis(createLatencies, 0.50),
        percentileMillis(createLatencies, 0.95),
        percentileMillis(createLatencies, 0.99),
        percentileMillis(readLatencies, 0.50),
        percentileMillis(readLatencies, 0.95),
        percentileMillis(readLatencies, 0.99),
        percentileMillis(flowLatencies, 0.50),
        percentileMillis(flowLatencies, 0.95),
        percentileMillis(flowLatencies, 0.99));
  }

  private double percentileMillis(List<Long> samples, double percentile) {
    List<Long> sorted = new ArrayList<>(samples);
    Collections.sort(sorted);
    int index = Math.max(0, (int) Math.ceil(sorted.size() * percentile) - 1);
    return Math.round(sorted.get(index) / 1_000_000.0 * 10.0) / 10.0;
  }
}
