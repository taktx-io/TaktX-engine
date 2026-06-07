/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.client;

import static org.assertj.core.api.Assertions.assertThat;

import io.taktx.dto.GlobalConfigurationDTO;
import io.taktx.security.RuntimeConfigurationHolder;
import io.taktx.security.SigningIdentity;
import io.taktx.security.SigningKeyGenerator;
import io.taktx.security.SigningServiceHolder;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
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
  void
      refreshWorkerSigningFunctionRegistration_ignoresLegacyRuntimeEnablementWithoutAnchoredPolicy() {
    SigningIdentity signingIdentity = signingIdentity("worker-key");

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

    assertThat(SigningServiceHolder.get()).isNull();
  }

  @Test
  void shouldKeepDefaultOpenClientSigningInactive() {
    TaktXClient client = clientWithSigningIdentity(false).client();

    // Without anchored mode (no platform key), signing should not be activated
    client.refreshWorkerSigningFunctionRegistration();

    assertThat(SigningServiceHolder.get()).isNull();
  }

  @Test
  void anchoredClient_preparesAndActivatesClientSigning() throws Exception {
    WorkerSigningFixture fixture = clientWithSigningIdentity(true);
    TaktXClient client = fixture.client();

    client.refreshWorkerSigningFunctionRegistration();
    markWorkerSigningKeyPublished(client, fixture.signingIdentity());

    assertThat(SigningServiceHolder.get()).isNotNull();
    assertThat(SigningServiceHolder.get().sign("payload".getBytes(StandardCharsets.UTF_8)))
        .startsWith("worker-key.");
  }

  @Test
  void anchoredClient_reactivatesClientSigningWithoutLegacyRuntimeToggle() throws Exception {
    WorkerSigningFixture fixture = clientWithSigningIdentity(true);
    TaktXClient client = fixture.client();

    client.refreshWorkerSigningFunctionRegistration();
    SigningServiceHolder.SigningFunction initialSigner = SigningServiceHolder.get();
    SigningServiceHolder.clear();

    client.refreshWorkerSigningFunctionRegistration();
    markWorkerSigningKeyPublished(client, fixture.signingIdentity());

    assertThat(SigningServiceHolder.get()).isNotNull().isSameAs(initialSigner);
    assertThat(SigningServiceHolder.get().sign("payload".getBytes(StandardCharsets.UTF_8)))
        .startsWith("worker-key.");
  }

  @Test
  void refreshWorkerSigningFunctionRegistration_keepsSignerActiveWithoutLegacyRuntimeConfiguration()
      throws Exception {
    SigningIdentity signingIdentity = signingIdentity("worker-key");

    Properties props = anchoredProperties();

    TaktXClient client =
        TaktXClient.newClientBuilder()
            .withProperties(props)
            .withSigningIdentitySource(() -> signingIdentity)
            .build();

    RuntimeConfigurationHolder.set(GlobalConfigurationDTO.builder().signingEnabled(true).build());
    client.refreshWorkerSigningFunctionRegistration();
    markWorkerSigningKeyPublished(client, signingIdentity);

    assertThat(SigningServiceHolder.get()).isNotNull();

    RuntimeConfigurationHolder.clear();

    assertThat(SigningServiceHolder.get().sign("payload".getBytes(StandardCharsets.UTF_8)))
        .startsWith("worker-key.");
  }

  @Test
  void refreshWorkerSigningFunctionRegistration_doesNotOverrideExistingGlobalSigner() {
    SigningIdentity signingIdentity = signingIdentity("worker-key");

    SigningServiceHolder.SigningFunction existingGlobalSigner = payload -> "engine-key.stub";
    SigningServiceHolder.set(existingGlobalSigner);

    TaktXClient client =
        TaktXClient.newClientBuilder()
            .withProperties(anchoredProperties())
            .withSigningIdentitySource(() -> signingIdentity)
            .build();

    RuntimeConfigurationHolder.set(GlobalConfigurationDTO.builder().signingEnabled(true).build());
    client.refreshWorkerSigningFunctionRegistration();

    assertThat(SigningServiceHolder.get()).isSameAs(existingGlobalSigner);
  }

  private static WorkerSigningFixture clientWithSigningIdentity(boolean anchored) {
    SigningIdentity signingIdentity = signingIdentity("worker-key");

    Properties props = anchored ? anchoredProperties() : openProperties();

    return new WorkerSigningFixture(
        TaktXClient.newClientBuilder()
            .withProperties(props)
            .withSigningIdentitySource(() -> signingIdentity)
            .build(),
        signingIdentity);
  }

  private static void markWorkerSigningKeyPublished(TaktXClient client, SigningIdentity identity)
      throws Exception {
    String descriptor =
        (String)
            invokeDeclaredMethod(
                client,
                "workerIdentityPublicationDescriptor",
                new Class<?>[] {SigningIdentity.class},
                identity);
    setField(client, "publishedWorkerIdentityDescriptor", descriptor);
    setField(client, "publishedWorkerKeyId", identity.getKeyId());
  }

  private static Object invokeDeclaredMethod(
      Object target, String methodName, Class<?>[] parameterTypes, Object... args)
      throws Exception {
    Method method = target.getClass().getDeclaredMethod(methodName, parameterTypes);
    method.setAccessible(true);
    return method.invoke(target, args);
  }

  private static void setField(Object target, String fieldName, Object value) throws Exception {
    Field field = target.getClass().getDeclaredField(fieldName);
    field.setAccessible(true);
    field.set(target, value);
  }

  private static Properties openProperties() {
    Properties props = new Properties();
    props.setProperty("bootstrap.servers", "localhost:9092");
    props.setProperty("taktx.engine.tenant-id", "test-tenant");
    props.setProperty("taktx.engine.namespace", "default");
    return props;
  }

  private static Properties anchoredProperties() {
    KeyPair platformKeyPair = SigningKeyGenerator.generate();
    Properties props = openProperties();
    props.setProperty(
        "taktx.platform.public-key",
        SigningKeyGenerator.encodePublicKey(platformKeyPair.getPublic()));
    // Fail-fast requires a registration signature when anchored.
    // These unit tests don't do actual countersignature verification, so any non-blank value works.
    props.setProperty("taktx.signing.registration-signature", "dGVzdC1yZWdpc3RyYXRpb24tc2ln");
    return props;
  }

  private static SigningIdentity signingIdentity(String keyId) {
    KeyPair keyPair = SigningKeyGenerator.generate();
    return SigningIdentity.ed25519(
        keyId,
        SigningKeyGenerator.encodePrivateKey(keyPair.getPrivate()),
        SigningKeyGenerator.encodePublicKey(keyPair.getPublic()));
  }

  private record WorkerSigningFixture(TaktXClient client, SigningIdentity signingIdentity) {}
}
