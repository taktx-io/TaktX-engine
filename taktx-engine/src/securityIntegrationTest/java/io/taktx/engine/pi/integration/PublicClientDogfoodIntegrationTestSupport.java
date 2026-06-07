/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.engine.pi.integration;

import static org.awaitility.Awaitility.await;

import io.taktx.client.ExternalTaskTriggerConsumer;
import io.taktx.client.InstanceUpdateRecord;
import io.taktx.client.TaktXClient;
import io.taktx.dto.ExecutionState;
import io.taktx.dto.ExternalTaskTriggerDTO;
import io.taktx.dto.KeyRole;
import io.taktx.dto.NamespaceSecurityPolicyDTO;
import io.taktx.dto.ParticipantCapability;
import io.taktx.dto.ParticipantKind;
import io.taktx.dto.ProcessInstanceUpdateDTO;
import io.taktx.dto.SecurityMode;
import io.taktx.dto.SecurityParticipantDescriptor;
import io.taktx.security.NamespaceSecurityPolicySupport;
import io.taktx.security.SigningKeyGenerator;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.kafka.clients.admin.AdminClient;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;

abstract class PublicClientDogfoodIntegrationTestSupport {

  protected static final String TENANT = "test-tenant";
  protected static final String DEFAULT_NAMESPACE = "default";
  protected static final String ISOLATED_NAMESPACE = "dogfood-isolated";
  protected static final String POLICY_WRITER_KEY_ID = "dogfood-policy-writer";
  protected static final String OPEN_PROCESS_ID = "task-single";
  protected static final String SERVICE_PROCESS_ID = "service-task-single";
  protected static final String SERVICE_TASK_TYPE = "service-task";
  private static final Set<String> BOOTSTRAPPED_NAMESPACES = ConcurrentHashMap.newKeySet();

  protected static final String TASK_SINGLE_BPMN =
      """
      <?xml version="1.0" encoding="UTF-8"?>
      <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" xmlns:modeler="http://camunda.org/schema/modeler/1.0" id="Definitions_0pw0wdt" targetNamespace="http://bpmn.io/schema/bpmn" exporter="Camunda Modeler" exporterVersion="5.19.0" modeler:executionPlatform="Camunda Cloud" modeler:executionPlatformVersion="8.4.0">
        <bpmn:process id="task-single" isExecutable="true">
          <bpmn:documentation>documen</bpmn:documentation>
          <bpmn:startEvent id="StartEvent_1">
            <bpmn:outgoing>Flow_0v3edbs</bpmn:outgoing>
          </bpmn:startEvent>
          <bpmn:sequenceFlow id="Flow_0v3edbs" sourceRef="StartEvent_1" targetRef="Task_1" />
          <bpmn:endEvent id="EndEvent_1">
            <bpmn:incoming>Flow_00uez6y</bpmn:incoming>
          </bpmn:endEvent>
          <bpmn:sequenceFlow id="Flow_00uez6y" sourceRef="Task_1" targetRef="EndEvent_1" />
          <bpmn:task id="Task_1" name="Task">
            <bpmn:incoming>Flow_0v3edbs</bpmn:incoming>
            <bpmn:outgoing>Flow_00uez6y</bpmn:outgoing>
          </bpmn:task>
        </bpmn:process>
        <bpmndi:BPMNDiagram id="BPMNDiagram_1">
          <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="task-single">
            <bpmndi:BPMNShape id="_BPMNShape_StartEvent_2" bpmnElement="StartEvent_1">
              <dc:Bounds x="179" y="99" width="36" height="36" />
            </bpmndi:BPMNShape>
            <bpmndi:BPMNShape id="EndEvent_1_di" bpmnElement="EndEvent_1">
              <dc:Bounds x="642" y="99" width="36" height="36" />
            </bpmndi:BPMNShape>
            <bpmndi:BPMNShape id="Activity_0jbzg6l_di" bpmnElement="Task_1">
              <dc:Bounds x="370" y="77" width="100" height="80" />
              <bpmndi:BPMNLabel />
            </bpmndi:BPMNShape>
            <bpmndi:BPMNEdge id="Flow_0v3edbs_di" bpmnElement="Flow_0v3edbs">
              <di:waypoint x="215" y="117" />
              <di:waypoint x="370" y="117" />
            </bpmndi:BPMNEdge>
            <bpmndi:BPMNEdge id="Flow_00uez6y_di" bpmnElement="Flow_00uez6y">
              <di:waypoint x="470" y="117" />
              <di:waypoint x="642" y="117" />
            </bpmndi:BPMNEdge>
          </bpmndi:BPMNPlane>
        </bpmndi:BPMNDiagram>
      </bpmn:definitions>
      """;

  protected static final String SERVICE_TASK_BPMN =
      """
      <?xml version="1.0" encoding="UTF-8"?>
      <bpmn:definitions xmlns:bpmn="http://www.omg.org/spec/BPMN/20100524/MODEL" xmlns:bpmndi="http://www.omg.org/spec/BPMN/20100524/DI" xmlns:dc="http://www.omg.org/spec/DD/20100524/DC" xmlns:zeebe="http://camunda.org/schema/zeebe/1.0" xmlns:di="http://www.omg.org/spec/DD/20100524/DI" xmlns:modeler="http://camunda.org/schema/modeler/1.0" id="Definitions_0pw0wdt" targetNamespace="http://bpmn.io/schema/bpmn" exporter="Camunda Modeler" exporterVersion="5.38.1" modeler:executionPlatform="Camunda Cloud" modeler:executionPlatformVersion="8.4.0">
        <bpmn:process id="service-task-single" isExecutable="true">
          <bpmn:documentation>documen</bpmn:documentation>
          <bpmn:startEvent id="StartEvent_1">
            <bpmn:extensionElements>
              <zeebe:ioMapping>
                <zeebe:output source="=&#34;outputValue1&#34;" target="StartEvent_Output_1" />
                <zeebe:output source="=&#34;outputValue2&#34;" target="StartEvent_Output_2" />
              </zeebe:ioMapping>
            </bpmn:extensionElements>
            <bpmn:outgoing>Flow_0v3edbs</bpmn:outgoing>
          </bpmn:startEvent>
          <bpmn:sequenceFlow id="Flow_0v3edbs" sourceRef="StartEvent_1" targetRef="ServiceTask_1" />
          <bpmn:endEvent id="EndEvent_1">
            <bpmn:incoming>Flow_00uez6y</bpmn:incoming>
          </bpmn:endEvent>
          <bpmn:sequenceFlow id="Flow_00uez6y" sourceRef="ServiceTask_1" targetRef="EndEvent_1" />
          <bpmn:serviceTask id="ServiceTask_1" name="ServiceTask">
            <bpmn:extensionElements>
              <zeebe:ioMapping>
                <zeebe:input source="=123" target="inputVariable" />
                <zeebe:output source="=var1" target="MappedOutputVariable" />
              </zeebe:ioMapping>
              <zeebe:taskDefinition type="=&#34;service-task&#34;" retries="5" />
              <zeebe:taskHeaders>
                <zeebe:header key="header1" value="headerValue1" />
                <zeebe:header key="header2" value="headerValue2" />
              </zeebe:taskHeaders>
            </bpmn:extensionElements>
            <bpmn:incoming>Flow_0v3edbs</bpmn:incoming>
            <bpmn:outgoing>Flow_00uez6y</bpmn:outgoing>
          </bpmn:serviceTask>
        </bpmn:process>
        <bpmndi:BPMNDiagram id="BPMNDiagram_1">
          <bpmndi:BPMNPlane id="BPMNPlane_1" bpmnElement="service-task-single">
            <bpmndi:BPMNShape id="_BPMNShape_StartEvent_2" bpmnElement="StartEvent_1">
              <dc:Bounds x="179" y="99" width="36" height="36" />
            </bpmndi:BPMNShape>
            <bpmndi:BPMNShape id="Event_0gfxmm9_di" bpmnElement="EndEvent_1">
              <dc:Bounds x="642" y="99" width="36" height="36" />
            </bpmndi:BPMNShape>
            <bpmndi:BPMNShape id="Activity_131f5e1_di" bpmnElement="ServiceTask_1">
              <dc:Bounds x="370" y="77" width="100" height="80" />
              <bpmndi:BPMNLabel />
            </bpmndi:BPMNShape>
            <bpmndi:BPMNEdge id="Flow_0v3edbs_di" bpmnElement="Flow_0v3edbs">
              <di:waypoint x="215" y="117" />
              <di:waypoint x="370" y="117" />
            </bpmndi:BPMNEdge>
            <bpmndi:BPMNEdge id="Flow_00uez6y_di" bpmnElement="Flow_00uez6y">
              <di:waypoint x="470" y="117" />
              <di:waypoint x="642" y="117" />
            </bpmndi:BPMNEdge>
          </bpmndi:BPMNPlane>
        </bpmndi:BPMNDiagram>
      </bpmn:definitions>
      """;

  protected static String bootstrapServers;
  private static String policyWriterPrivateKeyBase64;
  private static String policyWriterPublicKeyBase64;

  private final List<TaktXClient> startedClients = new CopyOnWriteArrayList<>();
  private final Set<String> namespacesUsedByCurrentTest = ConcurrentHashMap.newKeySet();

  @BeforeAll
  protected static void publishTrustedControlPlaneKeys() {
    bootstrapServers = ConfigProvider.getConfig().getValue("kafka.bootstrap.servers", String.class);

    KeyPair policyWriterKeys = SigningKeyGenerator.generate();
    policyWriterPrivateKeyBase64 =
        SigningKeyGenerator.encodePrivateKey(policyWriterKeys.getPrivate());
    policyWriterPublicKeyBase64 = SigningKeyGenerator.encodePublicKey(policyWriterKeys.getPublic());

    bootstrapNamespaceIfNeeded(DEFAULT_NAMESPACE);
    bootstrapNamespaceIfNeeded(ISOLATED_NAMESPACE);
    clearNamespaceSecurityPolicy(DEFAULT_NAMESPACE);
    clearNamespaceSecurityPolicy(ISOLATED_NAMESPACE);
  }

  protected final String newTestNamespace(String prefix) {
    String sanitizedPrefix =
        (prefix == null || prefix.isBlank())
            ? "dogfood"
            : prefix.toLowerCase().replaceAll("[^a-z0-9-]", "-").replaceAll("-+", "-");
    String namespace = stableHarnessNamespaceFor(sanitizedPrefix);
    namespacesUsedByCurrentTest.add(namespace);
    bootstrapNamespaceIfNeeded(namespace);
    clearNamespaceSecurityPolicy(namespace);
    return namespace;
  }

  private static String stableHarnessNamespaceFor(String sanitizedPrefix) {
    // The current in-JVM security dogfood harness runs a single engine instance bound to the
    // default namespace. Public-client scenarios that need active engine processing must therefore
    // stay on that engine-backed namespace. The isolated namespace remains useful for namespace
    // scoping / policy-only visibility scenarios where no engine-side runtime execution is needed.
    if (sanitizedPrefix.contains("isolated") || sanitizedPrefix.contains("cross-secured")) {
      return ISOLATED_NAMESPACE;
    }
    return DEFAULT_NAMESPACE;
  }

  private static void bootstrapNamespaceIfNeeded(String namespace) {
    if (BOOTSTRAPPED_NAMESPACES.contains(namespace)) {
      return;
    }
    synchronized (BOOTSTRAPPED_NAMESPACES) {
      if (BOOTSTRAPPED_NAMESPACES.add(namespace)) {
        publishTrustedControlPlaneKeysForNamespace(namespace);
      }
    }
  }

  private static void clearNamespaceSecurityPolicy(String namespace) {
    bootstrapNamespaceIfNeeded(namespace);
    TaktXClient.clearNamespaceSecurityPolicy(platformWriterProperties(namespace));
  }

  private static void publishTrustedControlPlaneKeysForNamespace(String namespace) {
    Properties namespaceProperties = baseProperties(namespace);
    String platformRegistrationSignature =
        registrationSignatureIfAvailable(
            SecurityTestConfigResource.PLATFORM_KID,
            SecurityTestConfigResource.rsaPublicKeyBase64,
            "RSA",
            KeyRole.PLATFORM);
    TaktXClient.publishSigningKey(
        namespaceProperties,
        SecurityTestConfigResource.PLATFORM_KID,
        SecurityTestConfigResource.rsaPublicKeyBase64,
        "RSA",
        KeyRole.PLATFORM,
        platformRegistrationSignature);
    String policyWriterRegistrationSignature =
        registrationSignatureIfAvailable(
            POLICY_WRITER_KEY_ID, policyWriterPublicKeyBase64, "Ed25519", KeyRole.PLATFORM);
    TaktXClient.publishSigningKey(
        namespaceProperties,
        POLICY_WRITER_KEY_ID,
        policyWriterPublicKeyBase64,
        "Ed25519",
        KeyRole.PLATFORM,
        policyWriterRegistrationSignature);
  }

  private static String registrationSignatureIfAvailable(
      String keyId, String publicKeyBase64, String algorithm, KeyRole role) {
    if (SecurityTestConfigResource.rsaPrivateKey == null
        || publicKeyBase64 == null
        || publicKeyBase64.isBlank()) {
      return null;
    }
    return SecurityTestConfigResource.registrationSignature(
        keyId, publicKeyBase64, algorithm, role);
  }

  @AfterEach
  protected void tearDownClientsAndPolicy() {
    List<TaktXClient> clients = new ArrayList<>(startedClients);
    startedClients.clear();
    for (TaktXClient client : clients.reversed()) {
      try {
        client.stop();
      } catch (Exception _) {
        // Best-effort cleanup — the next test creates fresh clients and unique consumer groups.
      }
    }
    for (String namespace : List.copyOf(namespacesUsedByCurrentTest)) {
      try {
        clearNamespaceSecurityPolicy(namespace);
      } catch (Exception _) {
        // Best-effort cleanup — unique namespaces limit cross-test leakage even if policy clear
        // races.
      }
    }
    namespacesUsedByCurrentTest.clear();
  }

  protected final TaktXClient startClient(
      Properties properties, SecurityParticipantDescriptor descriptor) {
    trackNamespace(properties);
    TaktXClient client =
        TaktXClient.newClientBuilder()
            .withProperties(properties)
            .withParticipantDescriptor(descriptor)
            .build();
    client.start();
    startedClients.add(client);
    return client;
  }

  protected final TaktXClient startClientWithoutSigningIdentity(
      Properties properties, SecurityParticipantDescriptor descriptor) {
    trackNamespace(properties);
    TaktXClient client =
        TaktXClient.newClientBuilder()
            .withProperties(properties)
            .withParticipantDescriptor(descriptor)
            .withSigningIdentitySource(() -> null)
            .build();
    client.start();
    startedClients.add(client);
    return client;
  }

  private void trackNamespace(Properties properties) {
    String namespace = properties.getProperty("taktx.engine.namespace", DEFAULT_NAMESPACE);
    namespacesUsedByCurrentTest.add(namespace);
    bootstrapNamespaceIfNeeded(namespace);
  }

  protected static ExternalTaskTriggerConsumer collectingExternalTaskConsumer(
      Queue<ExternalTaskTriggerDTO> triggers) {
    return new ExternalTaskTriggerConsumer() {
      @Override
      public Set<String> getJobIds() {
        return Set.of(SERVICE_TASK_TYPE);
      }

      @Override
      public void acceptBatch(List<ExternalTaskTriggerDTO> batch) {
        triggers.addAll(batch);
      }
    };
  }

  protected static void deployProcessAndAwaitAvailability(
      TaktXClient client, String bpmnXml, String processDefinitionId) throws Exception {
    AtomicReference<String> expectedHash = new AtomicReference<>();
    AtomicBoolean definitionObserved = new AtomicBoolean(false);
    client
        .runtime()
        .registerProcessDefinitionUpdateConsumer(
            (definitionKey, definition) -> {
              String hash = expectedHash.get();
              if (hash != null
                  && processDefinitionId.equals(definitionKey.getProcessDefinitionId())
                  && definition.getDefinitions() != null
                  && definition.getDefinitions().getDefinitionsKey() != null
                  && hash.equals(definition.getDefinitions().getDefinitionsKey().getHash())) {
                definitionObserved.set(true);
              }
            });
    var parsedDefinitions =
        client
            .runtime()
            .deployProcessDefinition(
                new ByteArrayInputStream(bpmnXml.getBytes(StandardCharsets.UTF_8)));
    expectedHash.set(parsedDefinitions.getDefinitionsKey().getHash());
    await()
        .atMost(Duration.ofSeconds(30))
        .pollInterval(Duration.ofMillis(100))
        .until(
            () ->
                definitionObserved.get()
                    || client
                        .runtime()
                        .getProcessDefinitionByHash(
                            processDefinitionId, parsedDefinitions.getDefinitionsKey().getHash())
                        .isPresent());
  }

  protected static void awaitNoPolicy(TaktXClient client) {
    client
        .observability()
        .awaitPostureSnapshot(
            snapshot -> snapshot.effectiveMode() != SecurityMode.ANCHORED, Duration.ofSeconds(30));
  }

  protected static ExternalTaskTriggerDTO awaitExternalTaskTrigger(
      Queue<ExternalTaskTriggerDTO> triggers, UUID processInstanceId) {
    await()
        .atMost(Duration.ofSeconds(30))
        .pollInterval(Duration.ofMillis(100))
        .until(
            () ->
                triggers.stream()
                    .anyMatch(trigger -> processInstanceId.equals(trigger.getProcessInstanceId())));
    return triggers.stream()
        .filter(trigger -> processInstanceId.equals(trigger.getProcessInstanceId()))
        .findFirst()
        .orElseThrow();
  }

  protected static void awaitProcessCompleted(
      Queue<InstanceUpdateRecord> updates, UUID processInstanceId) {
    await()
        .atMost(Duration.ofSeconds(30))
        .pollInterval(Duration.ofMillis(100))
        .until(
            () ->
                updates.stream()
                    .filter(
                        updateRecord ->
                            processInstanceId.equals(updateRecord.getProcessInstanceId()))
                    .map(InstanceUpdateRecord::getUpdate)
                    .filter(ProcessInstanceUpdateDTO.class::isInstance)
                    .map(ProcessInstanceUpdateDTO.class::cast)
                    .anyMatch(
                        update ->
                            update.getScope() != null
                                && update.getScope().getState() == ExecutionState.COMPLETED));
  }

  protected static void awaitTopicExists(String topicName) {
    await()
        .atMost(Duration.ofSeconds(30))
        .pollInterval(Duration.ofMillis(200))
        .until(
            () -> {
              Properties properties = new Properties();
              properties.put("bootstrap.servers", bootstrapServers);
              try (AdminClient adminClient = AdminClient.create(properties)) {
                return adminClient.listTopics().names().get().contains(topicName);
              }
            });
  }

  protected static NamespaceSecurityPolicyDTO activeAnchoredPolicy() {
    return NamespaceSecurityPolicySupport.requireValid(
        NamespaceSecurityPolicyDTO.builder().mode(SecurityMode.ANCHORED).build());
  }

  protected static SecurityParticipantDescriptor participantDescriptor(
      String participantId, Set<ParticipantCapability> capabilities, String componentType) {
    return new SecurityParticipantDescriptor(
        participantId, ParticipantKind.CLIENT, capabilities, componentType);
  }

  protected static Properties baseProperties(String namespace) {
    Properties properties = new Properties();
    properties.setProperty("bootstrap.servers", bootstrapServers);
    properties.setProperty("taktx.engine.tenant-id", TENANT);
    properties.setProperty("taktx.engine.namespace", namespace);
    return properties;
  }

  protected static Properties platformWriterProperties(String namespace) {
    Properties properties = baseProperties(namespace);
    properties.setProperty("taktx.signing.key-id", POLICY_WRITER_KEY_ID);
    properties.setProperty("taktx.signing.private-key", policyWriterPrivateKeyBase64);
    properties.setProperty("taktx.signing.public-key", policyWriterPublicKeyBase64);
    String registrationSignature =
        registrationSignatureIfAvailable(
            POLICY_WRITER_KEY_ID, policyWriterPublicKeyBase64, "Ed25519", KeyRole.PLATFORM);
    if (registrationSignature != null) {
      properties.setProperty("taktx.signing.registration-signature", registrationSignature);
    }
    return properties;
  }
}
