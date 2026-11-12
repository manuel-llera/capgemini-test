package com.capgemini.test.code.support;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

public final class ExternalServiceStub implements AutoCloseable {
  private final HttpServer server;
  private final ConcurrentLinkedQueue<RecordedRequest> requests = new ConcurrentLinkedQueue<>();
  private volatile int notificationStatus = 200;

  public ExternalServiceStub() {
    try {
      server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    } catch (IOException exception) {
      throw new IllegalStateException("Could not start external service test stub", exception);
    }

    server.createContext("/check-dni", exchange -> handleDniCheck(exchange));
    server.createContext("/email", exchange -> handleNotification(exchange, notificationStatus));
    server.createContext("/sms", exchange -> handleNotification(exchange, notificationStatus));
    server.start();
  }

  public String baseUrl() {
    return "http://127.0.0.1:" + server.getAddress().getPort();
  }

  public void setNotificationStatus(int notificationStatus) {
    this.notificationStatus = notificationStatus;
  }

  public void reset() {
    requests.clear();
    notificationStatus = 200;
  }

  public List<RecordedRequest> requests() {
    return List.copyOf(requests);
  }

  @Override
  public void close() {
    server.stop(0);
  }

  private void handleDniCheck(HttpExchange exchange) throws IOException {
    String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    requests.add(
        new RecordedRequest(
            exchange.getRequestMethod(), exchange.getRequestURI().getPath(), requestBody));
    boolean invalidDni = requestBody.contains("\"dni\":\"99999999w\"");
    respond(
        exchange,
        invalidDni ? 409 : 200,
        invalidDni ? "{\"message\":\"Invalid DNI\"}" : "{\"message\":\"Valid DNI\"}");
  }

  private void handleNotification(HttpExchange exchange, int status) throws IOException {
    String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
    requests.add(
        new RecordedRequest(
            exchange.getRequestMethod(), exchange.getRequestURI().getPath(), requestBody));
    respond(exchange, status, status == 200 ? "" : "{\"message\":\"Notification failed\"}");
  }

  private void respond(HttpExchange exchange, int status, String body) throws IOException {
    byte[] responseBody = body.getBytes(StandardCharsets.UTF_8);
    if (responseBody.length > 0) {
      exchange.getResponseHeaders().set("Content-Type", "application/json");
      exchange.sendResponseHeaders(status, responseBody.length);
      exchange.getResponseBody().write(responseBody);
    } else {
      exchange.sendResponseHeaders(status, -1);
    }
    exchange.close();
  }

  public record RecordedRequest(String method, String path, String body) {}
}
