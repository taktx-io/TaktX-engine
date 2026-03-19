/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */
package io.taktx.client;

import static org.assertj.core.api.Assertions.assertThat;

import io.taktx.dto.GlobalConfigurationDTO;
import io.taktx.security.RuntimeConfigurationHolder;
import io.taktx.security.SigningIdentity;
import io.taktx.security.SigningKeyGenerator;
import io.taktx.security.SigningServiceHolder;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.util.Properties;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TaktXClientWorkerSigningTest {

  @AfterEach
  void tearDown() {
    RuntimeConfigurationHolder.clear();
    SigningServiceHolder.clear();
  }

  @Test
  void refreshWorkerSigningFunctionRegistration_registersSignerAfterRuntimeEnablement() {
    KeyPair keyPair = SigningKeyGenerator.generate();
    String privateKeyBase64 = SigningKeyGenerator.encodePrivateKey(keyPair.getPrivate());
    SigningIdentity signingIdentity = SigningIdentity.ed25519("worker-key", privateKeyBase64, null);

    Properties props = new Properties();
    props.setProperty("bootstrap.servers", "localhost:9092");
    props.setProperty("taktx.engine.tenant-id", "test-tenant");
    props.setProperty("taktx.engine.namespace", "default");

    TaktXClient client =
        TaktXClient.newClientBuilder()
            .withProperties(props)
            .withSigningIdentitySource(() -> signingIdentity)
            .build();

    assertThat(SigningServiceHolder.get()).isNull();

    RuntimeConfigurationHolder.set(GlobalConfigurationDTO.builder().signingEnabled(true).build());
    client.refreshWorkerSigningFunctionRegistration();

    assertThat(SigningServiceHolder.get()).isNotNull();
    assertThat(SigningServiceHolder.get().sign("payload".getBytes(StandardCharsets.UTF_8)))
        .startsWith("worker-key.");
  }

  @Test
  void refreshWorkerSigningFunctionRegistration_keepsSignerInactiveWhileRuntimeSigningDisabled() {
    KeyPair keyPair = SigningKeyGenerator.generate();
    String privateKeyBase64 = SigningKeyGenerator.encodePrivateKey(keyPair.getPrivate());
    SigningIdentity signingIdentity = SigningIdentity.ed25519("worker-key", privateKeyBase64, null);

    Properties props = new Properties();
    props.setProperty("bootstrap.servers", "localhost:9092");
    props.setProperty("taktx.engine.tenant-id", "test-tenant");
    props.setProperty("taktx.engine.namespace", "default");

    TaktXClient client =
        TaktXClient.newClientBuilder()
            .withProperties(props)
            .withSigningIdentitySource(() -> signingIdentity)
            .build();

    RuntimeConfigurationHolder.set(GlobalConfigurationDTO.builder().signingEnabled(true).build());
    client.refreshWorkerSigningFunctionRegistration();

    assertThat(SigningServiceHolder.get()).isNotNull();

    RuntimeConfigurationHolder.clear();

    assertThat(SigningServiceHolder.get().sign("payload".getBytes(StandardCharsets.UTF_8)))
        .isNull();
  }
}
