/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class OpenIdClientCredentialsTokenProviderTest {

  private HttpServer server;

  @AfterEach
  void tearDown() {
    if (server != null) {
      server.stop(0);
    }
  }

  @Test
  void getAuthorizationToken_fetchesAndCachesTokensUntilRefreshWindow() throws Exception {
    AtomicInteger requestCount = new AtomicInteger();
    AtomicReference<String> authorizationHeader = new AtomicReference<>();
    AtomicReference<String> requestBody = new AtomicReference<>();
    server =
        startServer(
            exchange -> {
              requestCount.incrementAndGet();
              authorizationHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
              requestBody.set(readBody(exchange));
              writeJson(exchange, 200, "{\"access_token\":\"token-1\",\"expires_in\":120}");
            });

    MutableClock clock = new MutableClock(Instant.parse("2026-03-19T10:15:30Z"));
    OpenIdClientCredentialsTokenProvider provider =
        new OpenIdClientCredentialsTokenProvider(
            HttpClient.newHttpClient(),
            URI.create("http://localhost:" + server.getAddress().getPort() + "/oauth/token"),
            "client-id",
            "client-secret",
            "scope-a scope-b",
            "taktx-api",
            OpenIdClientCredentialsTokenProvider.ClientAuthenticationMethod.CLIENT_SECRET_BASIC,
            Duration.ofSeconds(5),
            Duration.ofSeconds(30),
            clock);

    String firstToken =
        provider.getAuthorizationToken(
            CommandAuthorizationRequest.startProcess("invoice", 1, java.util.UUID.randomUUID()));
    String secondToken =
        provider.getAuthorizationToken(
            CommandAuthorizationRequest.abortProcessInstance(
                java.util.UUID.randomUUID(), java.util.List.of()));

    assertThat(firstToken).isEqualTo("token-1");
    assertThat(secondToken).isEqualTo("token-1");
    assertThat(requestCount.get()).isEqualTo(1);
    assertThat(authorizationHeader.get()).startsWith("Basic ");
    assertThat(requestBody.get()).contains("grant_type=client_credentials");
    assertThat(requestBody.get()).contains("scope=scope-a+scope-b");
    assertThat(requestBody.get()).contains("audience=taktx-api");

    clock.advance(Duration.ofSeconds(95));
    provider.getAuthorizationToken(
        CommandAuthorizationRequest.startProcess("invoice", 1, java.util.UUID.randomUUID()));

    assertThat(requestCount.get()).isEqualTo(2);
  }

  @Test
  void getAuthorizationToken_supportsClientSecretPostAuthentication() throws Exception {
    AtomicReference<String> authorizationHeader = new AtomicReference<>();
    AtomicReference<String> requestBody = new AtomicReference<>();
    server =
        startServer(
            exchange -> {
              authorizationHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
              requestBody.set(readBody(exchange));
              writeJson(exchange, 200, "{\"access_token\":\"token-post\",\"expires_in\":60}");
            });

    OpenIdClientCredentialsTokenProvider provider =
        new OpenIdClientCredentialsTokenProvider(
            HttpClient.newHttpClient(),
            URI.create("http://localhost:" + server.getAddress().getPort() + "/oauth/token"),
            "client-id",
            "client-secret",
            null,
            null,
            OpenIdClientCredentialsTokenProvider.ClientAuthenticationMethod.CLIENT_SECRET_POST,
            Duration.ofSeconds(5),
            Duration.ofSeconds(5),
            Clock.systemUTC());

    assertThat(
            provider.getAuthorizationToken(
                CommandAuthorizationRequest.startProcess(
                    "invoice", 1, java.util.UUID.randomUUID())))
        .isEqualTo("token-post");
    assertThat(authorizationHeader.get()).isNull();
    assertThat(requestBody.get()).contains("client_id=client-id");
    assertThat(requestBody.get()).contains("client_secret=client-secret");
  }

  @Test
  void getAuthorizationToken_throwsWhenEndpointReturnsError() throws Exception {
    server = startServer(exchange -> writeJson(exchange, 401, "{\"error\":\"invalid_client\"}"));

    OpenIdClientCredentialsTokenProvider provider =
        new OpenIdClientCredentialsTokenProvider(
            URI.create("http://localhost:" + server.getAddress().getPort() + "/oauth/token"),
            "client-id",
            "client-secret",
            null,
            null);

    assertThatThrownBy(
            () ->
                provider.getAuthorizationToken(
                    CommandAuthorizationRequest.startProcess(
                        "invoice", 1, java.util.UUID.randomUUID())))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("returned HTTP 401");
  }

  @Test
  void fromProperties_requiresTokenEndpointAndClientCredentials() {
    assertThatThrownBy(
            () -> OpenIdClientCredentialsTokenProvider.fromProperties(new java.util.Properties()))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("taktx.authorization.openid.token-endpoint");
  }

  private HttpServer startServer(ExchangeHandler handler) throws IOException {
    HttpServer httpServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    httpServer.createContext("/oauth/token", exchange -> handler.handle(exchange));
    httpServer.start();
    return httpServer;
  }

  private static String readBody(HttpExchange exchange) throws IOException {
    return new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
  }

  private static void writeJson(HttpExchange exchange, int statusCode, String body)
      throws IOException {
    byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
    exchange.getResponseHeaders().put("Content-Type", java.util.List.of("application/json"));
    exchange.sendResponseHeaders(statusCode, bytes.length);
    exchange.getResponseBody().write(bytes);
    exchange.close();
  }

  @FunctionalInterface
  private interface ExchangeHandler {
    void handle(HttpExchange exchange) throws IOException;
  }

  private static final class MutableClock extends Clock {
    private Instant current;

    private MutableClock(Instant current) {
      this.current = current;
    }

    @Override
    public ZoneOffset getZone() {
      return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(java.time.ZoneId zone) {
      return this;
    }

    @Override
    public Instant instant() {
      return current;
    }

    private void advance(Duration duration) {
      current = current.plus(duration);
    }
  }
}
