/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */
package io.taktx.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.taktx.client.auth.AuthorizationTokenProvider;
import io.taktx.client.auth.OpenIdClientCredentialsTokenProvider;
import java.util.Properties;
import org.junit.jupiter.api.Test;

class TaktXClientBuilderAuthorizationTokenProviderTest {

  @Test
  void resolveAuthorizationTokenProvider_prefersExplicitProvider() {
    AuthorizationTokenProvider explicitProvider = request -> "explicit";
    TaktXClient.TaktXClientBuilder builder = TaktXClient.newClientBuilder();

    AuthorizationTokenProvider resolved =
        builder
            .withAuthorizationTokenProvider(explicitProvider)
            .resolveAuthorizationTokenProvider(new Properties());

    assertThat(resolved).isSameAs(explicitProvider);
  }

  @Test
  void resolveAuthorizationTokenProvider_returnsNullWhenNoAuthorizationConfigExists() {
    TaktXClient.TaktXClientBuilder builder = TaktXClient.newClientBuilder();

    assertThat(builder.resolveAuthorizationTokenProvider(new Properties())).isNull();
  }

  @Test
  void resolveAuthorizationTokenProvider_supportsOpenIdClientCredentialsConfig() {
    Properties properties = new Properties();
    properties.setProperty("taktx.authorization.token-provider", "openid-client-credentials");
    properties.setProperty(
        "taktx.authorization.openid.token-endpoint", "https://issuer.example/token");
    properties.setProperty("taktx.authorization.openid.client-id", "machine-client");
    properties.setProperty("taktx.authorization.openid.client-secret", "secret-123");
    properties.setProperty("taktx.authorization.openid.scope", "taktx.start");

    TaktXClient.TaktXClientBuilder builder = TaktXClient.newClientBuilder();

    assertThat(builder.resolveAuthorizationTokenProvider(properties))
        .isInstanceOf(OpenIdClientCredentialsTokenProvider.class);
  }

  @Test
  void resolveAuthorizationTokenProvider_rejectsUnsupportedProviderId() {
    Properties properties = new Properties();
    properties.setProperty("taktx.authorization.token-provider", "static-token");
    properties.setProperty(
        "taktx.authorization.openid.token-endpoint", "https://issuer.example/token");
    properties.setProperty("taktx.authorization.openid.client-id", "machine-client");
    properties.setProperty("taktx.authorization.openid.client-secret", "secret-123");

    TaktXClient.TaktXClientBuilder builder = TaktXClient.newClientBuilder();

    assertThatThrownBy(() -> builder.resolveAuthorizationTokenProvider(properties))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("taktx.authorization.token-provider")
        .hasMessageContaining("openid-client-credentials");
  }
}
