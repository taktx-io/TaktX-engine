/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.Nullable;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.Properties;

/** OAuth2/OpenID Connect client-credentials token provider backed by the JDK {@link HttpClient}. */
public class OpenIdClientCredentialsTokenProvider implements AuthorizationTokenProvider {

  public static final String TOKEN_PROVIDER_ID = "openid-client-credentials";

  private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
  private static final Duration DEFAULT_CONNECT_TIMEOUT = Duration.ofSeconds(3);
  private static final Duration DEFAULT_REQUEST_TIMEOUT = Duration.ofSeconds(5);
  private static final Duration DEFAULT_REFRESH_SKEW = Duration.ofSeconds(30);

  private final HttpClient httpClient;
  private final URI tokenEndpoint;
  private final String clientId;
  private final String clientSecret;
  private final @Nullable String scope;
  private final @Nullable String audience;
  private final ClientAuthenticationMethod clientAuthenticationMethod;
  private final Duration requestTimeout;
  private final Duration refreshSkew;
  private final Clock clock;

  private volatile CachedToken cachedToken;

  public OpenIdClientCredentialsTokenProvider(
      URI tokenEndpoint,
      String clientId,
      String clientSecret,
      @Nullable String scope,
      @Nullable String audience) {
    this(
        HttpClient.newBuilder().connectTimeout(DEFAULT_CONNECT_TIMEOUT).build(),
        tokenEndpoint,
        clientId,
        clientSecret,
        scope,
        audience,
        ClientAuthenticationMethod.CLIENT_SECRET_BASIC,
        DEFAULT_REQUEST_TIMEOUT,
        DEFAULT_REFRESH_SKEW,
        Clock.systemUTC());
  }

  OpenIdClientCredentialsTokenProvider(
      HttpClient httpClient,
      URI tokenEndpoint,
      String clientId,
      String clientSecret,
      @Nullable String scope,
      @Nullable String audience,
      ClientAuthenticationMethod clientAuthenticationMethod,
      Duration requestTimeout,
      Duration refreshSkew,
      Clock clock) {
    this.httpClient = Objects.requireNonNull(httpClient, "httpClient must not be null");
    this.tokenEndpoint = Objects.requireNonNull(tokenEndpoint, "tokenEndpoint must not be null");
    this.clientId = requireNonBlank(clientId, "clientId");
    this.clientSecret = requireNonBlank(clientSecret, "clientSecret");
    this.scope = blankToNull(scope);
    this.audience = blankToNull(audience);
    this.clientAuthenticationMethod =
        Objects.requireNonNull(
            clientAuthenticationMethod, "clientAuthenticationMethod must not be null");
    this.requestTimeout = Objects.requireNonNull(requestTimeout, "requestTimeout must not be null");
    this.refreshSkew = Objects.requireNonNull(refreshSkew, "refreshSkew must not be null");
    this.clock = Objects.requireNonNull(clock, "clock must not be null");
  }

  @Override
  public String getAuthorizationToken(CommandAuthorizationRequest request) {
    Instant now = clock.instant();
    CachedToken local = cachedToken;
    if (local != null && local.isUsableAt(now, refreshSkew)) {
      return local.accessToken();
    }
    synchronized (this) {
      local = cachedToken;
      if (local != null && local.isUsableAt(now, refreshSkew)) {
        return local.accessToken();
      }
      CachedToken refreshed = fetchToken();
      cachedToken = refreshed;
      return refreshed.accessToken();
    }
  }

  public static boolean hasConfiguration(Properties properties) {
    return !firstNonBlank(
                property(properties, "taktx.authorization.token-provider"),
                System.getProperty("taktx.authorization.token-provider"),
                System.getenv("TAKTX_AUTHORIZATION_TOKEN_PROVIDER"))
            .isBlank()
        || !firstNonBlank(
                property(properties, "taktx.authorization.openid.token-endpoint"),
                System.getProperty("taktx.authorization.openid.token-endpoint"),
                System.getenv("TAKTX_AUTHORIZATION_OPENID_TOKEN_ENDPOINT"))
            .isBlank();
  }

  public static OpenIdClientCredentialsTokenProvider fromProperties(Properties properties) {
    String tokenProvider =
        firstNonBlank(
            property(properties, "taktx.authorization.token-provider"),
            System.getProperty("taktx.authorization.token-provider"),
            System.getenv("TAKTX_AUTHORIZATION_TOKEN_PROVIDER"));
    if (!tokenProvider.isBlank() && !TOKEN_PROVIDER_ID.equalsIgnoreCase(tokenProvider)) {
      throw new IllegalArgumentException(
          "Unsupported taktx.authorization.token-provider='"
              + tokenProvider
              + "'. Supported values: "
              + TOKEN_PROVIDER_ID);
    }

    String endpoint =
        requireConfigured(
            properties,
            "taktx.authorization.openid.token-endpoint",
            "TAKTX_AUTHORIZATION_OPENID_TOKEN_ENDPOINT");
    String clientId =
        requireConfigured(
            properties,
            "taktx.authorization.openid.client-id",
            "TAKTX_AUTHORIZATION_OPENID_CLIENT_ID");
    String clientSecret =
        requireConfigured(
            properties,
            "taktx.authorization.openid.client-secret",
            "TAKTX_AUTHORIZATION_OPENID_CLIENT_SECRET");
    String scope =
        blankToNull(
            firstNonBlank(
                property(properties, "taktx.authorization.openid.scope"),
                System.getProperty("taktx.authorization.openid.scope"),
                System.getenv("TAKTX_AUTHORIZATION_OPENID_SCOPE")));
    String audience =
        blankToNull(
            firstNonBlank(
                property(properties, "taktx.authorization.openid.audience"),
                System.getProperty("taktx.authorization.openid.audience"),
                System.getenv("TAKTX_AUTHORIZATION_OPENID_AUDIENCE")));
    String authMethodValue =
        firstNonBlank(
            property(properties, "taktx.authorization.openid.client-auth-method"),
            System.getProperty("taktx.authorization.openid.client-auth-method"),
            System.getenv("TAKTX_AUTHORIZATION_OPENID_CLIENT_AUTH_METHOD"));
    ClientAuthenticationMethod authMethod =
        ClientAuthenticationMethod.fromValue(authMethodValue.isBlank() ? null : authMethodValue);
    Duration connectTimeout =
        durationProperty(
            properties,
            "taktx.authorization.openid.connect-timeout-ms",
            "TAKTX_AUTHORIZATION_OPENID_CONNECT_TIMEOUT_MS",
            DEFAULT_CONNECT_TIMEOUT);
    Duration requestTimeout =
        durationProperty(
            properties,
            "taktx.authorization.openid.request-timeout-ms",
            "TAKTX_AUTHORIZATION_OPENID_REQUEST_TIMEOUT_MS",
            DEFAULT_REQUEST_TIMEOUT);
    Duration refreshSkew =
        durationProperty(
            properties,
            "taktx.authorization.openid.refresh-skew-ms",
            "TAKTX_AUTHORIZATION_OPENID_REFRESH_SKEW_MS",
            DEFAULT_REFRESH_SKEW);

    HttpClient httpClient = HttpClient.newBuilder().connectTimeout(connectTimeout).build();
    return new OpenIdClientCredentialsTokenProvider(
        httpClient,
        URI.create(endpoint),
        clientId,
        clientSecret,
        scope,
        audience,
        authMethod,
        requestTimeout,
        refreshSkew,
        Clock.systemUTC());
  }

  private CachedToken fetchToken() {
    Instant now = clock.instant();
    HttpRequest request = buildRequest();
    HttpResponse<String> response;
    try {
      response =
          httpClient.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
    } catch (Exception e) {
      throw new IllegalStateException(
          "Failed to retrieve OAuth2 client-credentials token from " + tokenEndpoint, e);
    }
    if (response.statusCode() / 100 != 2) {
      throw new IllegalStateException(
          "OAuth2 token endpoint "
              + tokenEndpoint
              + " returned HTTP "
              + response.statusCode()
              + ": "
              + response.body());
    }

    try {
      JsonNode payload = OBJECT_MAPPER.readTree(response.body());
      String accessToken = textValue(payload, "access_token");
      if (accessToken == null || accessToken.isBlank()) {
        throw new IllegalStateException(
            "OAuth2 token endpoint response did not contain a non-blank access_token");
      }
      long expiresInSeconds = Math.max(payload.path("expires_in").asLong(300L), 1L);
      return new CachedToken(accessToken, now.plusSeconds(expiresInSeconds));
    } catch (RuntimeException e) {
      throw e;
    } catch (Exception e) {
      throw new IllegalStateException(
          "Failed to parse OAuth2 token endpoint response from " + tokenEndpoint, e);
    }
  }

  private HttpRequest buildRequest() {
    List<String> formPairs = new ArrayList<>();
    formPairs.add(formPair("grant_type", "client_credentials"));
    if (scope != null) {
      formPairs.add(formPair("scope", scope));
    }
    if (audience != null) {
      formPairs.add(formPair("audience", audience));
    }
    if (clientAuthenticationMethod == ClientAuthenticationMethod.CLIENT_SECRET_POST) {
      formPairs.add(formPair("client_id", clientId));
      formPairs.add(formPair("client_secret", clientSecret));
    }

    HttpRequest.Builder builder =
        HttpRequest.newBuilder(tokenEndpoint)
            .timeout(requestTimeout)
            .header("Content-Type", "application/x-www-form-urlencoded")
            .POST(
                HttpRequest.BodyPublishers.ofString(
                    String.join("&", formPairs), StandardCharsets.UTF_8));

    if (clientAuthenticationMethod == ClientAuthenticationMethod.CLIENT_SECRET_BASIC) {
      String basic =
          Base64.getEncoder()
              .encodeToString((clientId + ":" + clientSecret).getBytes(StandardCharsets.UTF_8));
      builder.header("Authorization", "Basic " + basic);
    }

    return builder.build();
  }

  private static String formPair(String key, String value) {
    return URLEncoder.encode(key, StandardCharsets.UTF_8)
        + "="
        + URLEncoder.encode(value, StandardCharsets.UTF_8);
  }

  private static Duration durationProperty(
      Properties properties, String propertyName, String envName, Duration defaultValue) {
    String raw =
        firstNonBlank(
            property(properties, propertyName),
            System.getProperty(propertyName),
            System.getenv(envName));
    if (raw.isBlank()) {
      return defaultValue;
    }
    return Duration.ofMillis(Long.parseLong(raw));
  }

  private static String requireConfigured(
      Properties properties, String propertyName, String envName) {
    String value =
        firstNonBlank(
            property(properties, propertyName),
            System.getProperty(propertyName),
            System.getenv(envName));
    if (value.isBlank()) {
      throw new IllegalArgumentException(
          propertyName + " is required (or environment variable " + envName + ")");
    }
    return value;
  }

  private static String requireNonBlank(String value, String fieldName) {
    if (value == null || value.isBlank()) {
      throw new IllegalArgumentException(fieldName + " must not be blank");
    }
    return value;
  }

  private static @Nullable String textValue(JsonNode node, String fieldName) {
    JsonNode child = node.path(fieldName);
    return child.isMissingNode() || child.isNull() ? null : child.asText();
  }

  private static String property(Properties properties, String key) {
    return properties != null ? properties.getProperty(key) : null;
  }

  private static String firstNonBlank(String... values) {
    for (String value : values) {
      if (value != null && !value.isBlank()) {
        return value;
      }
    }
    return "";
  }

  private static @Nullable String blankToNull(@Nullable String value) {
    return value == null || value.isBlank() ? null : value;
  }

  enum ClientAuthenticationMethod {
    CLIENT_SECRET_BASIC,
    CLIENT_SECRET_POST;

    static ClientAuthenticationMethod fromValue(@Nullable String value) {
      if (value == null || value.isBlank()) {
        return CLIENT_SECRET_BASIC;
      }
      if ("client_secret_basic".equalsIgnoreCase(value)) {
        return CLIENT_SECRET_BASIC;
      }
      if ("client_secret_post".equalsIgnoreCase(value)) {
        return CLIENT_SECRET_POST;
      }
      throw new IllegalArgumentException(
          "Unsupported taktx.authorization.openid.client-auth-method='"
              + value
              + "'. Supported values: client_secret_basic, client_secret_post");
    }
  }

  private record CachedToken(String accessToken, Instant expiresAt) {
    private boolean isUsableAt(Instant now, Duration refreshSkew) {
      return expiresAt.isAfter(now.plus(refreshSkew));
    }
  }
}
