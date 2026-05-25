/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.engine.pi.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.taktx.client.ExternalTaskTriggerConsumer;
import io.taktx.client.InstanceUpdateRecord;
import io.taktx.client.ObservedPolicySnapshot;
import io.taktx.client.SecurityPostureSnapshot;
import io.taktx.client.TaktXClient;
import io.taktx.dto.ExecutionState;
import io.taktx.dto.ExternalTaskTriggerDTO;
import io.taktx.dto.KeyRole;
import io.taktx.dto.NamespaceSecurityPolicyDTO;
import io.taktx.dto.ParticipantCapability;
import io.taktx.dto.ParticipantKind;
import io.taktx.dto.ParticipantStatusDTO;
import io.taktx.dto.ProcessInstanceUpdateDTO;
import io.taktx.dto.RequiredAuthorizationDTO;
import io.taktx.dto.RequiredSigningDTO;
import io.taktx.dto.SecurityActivationState;
import io.taktx.dto.SecurityEventDTO;
import io.taktx.dto.SecurityEventType;
import io.taktx.dto.SecurityMode;
import io.taktx.dto.SecurityParticipantDescriptor;
import io.taktx.dto.VariablesDTO;
import io.taktx.security.SigningKeyGenerator;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Properties;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.atomic.AtomicLong;
import org.apache.kafka.clients.admin.AdminClient;
import org.eclipse.microprofile.config.ConfigProvider;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@QuarkusTest
@TestProfile(SecurityTestProfile.class)
@QuarkusTestResource(value = SecurityTestConfigResource.class, restrictToAnnotatedClass = true)
@Tag("security-integration")
class PublicClientDogfoodIntegrationTest {

  private static final String TENANT = "test-tenant";
  private static final String DEFAULT_NAMESPACE = "default";
  private static final String ISOLATED_NAMESPACE = "dogfood-isolated";
  private static final String ISSUER = "taktx-dogfood";
  private static final String POLICY_WRITER_KEY_ID = "dogfood-policy-writer";
  private static final String ROGUE_WRITER_KEY_ID = "dogfood-rogue-writer";
  private static final String OPEN_PROCESS_ID = "task-single";
  private static final String SERVICE_PROCESS_ID = "service-task-single";
  private static final String SERVICE_TASK_TYPE = "service-task";
  private static final AtomicLong POLICY_VERSIONS = new AtomicLong(10_000L);

  private static final String TASK_SINGLE_BPMN =
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

  private static final String SERVICE_TASK_BPMN =
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

  private static String bootstrapServers;
  private static String policyWriterPrivateKeyBase64;
  private static String policyWriterPublicKeyBase64;
  private static String rogueWriterPrivateKeyBase64;
  private static String rogueWriterPublicKeyBase64;

  private final List<TaktXClient> startedClients = new CopyOnWriteArrayList<>();

  @BeforeAll
  static void publishTrustedControlPlaneKeys() {
    bootstrapServers =
        ConfigProvider.getConfig().getValue("kafka.bootstrap.servers", String.class);

    KeyPair policyWriterKeys = SigningKeyGenerator.generate();
    policyWriterPrivateKeyBase64 =
        SigningKeyGenerator.encodePrivateKey(policyWriterKeys.getPrivate());
    policyWriterPublicKeyBase64 = SigningKeyGenerator.encodePublicKey(policyWriterKeys.getPublic());

    KeyPair rogueWriterKeys = SigningKeyGenerator.generate();
    rogueWriterPrivateKeyBase64 = SigningKeyGenerator.encodePrivateKey(rogueWriterKeys.getPrivate());
    rogueWriterPublicKeyBase64 = SigningKeyGenerator.encodePublicKey(rogueWriterKeys.getPublic());

    Properties defaultNamespaceProperties = baseProperties(DEFAULT_NAMESPACE);
    TaktXClient.publishSigningKey(
        defaultNamespaceProperties,
        SecurityTestConfigResource.PLATFORM_KID,
        SecurityTestConfigResource.rsaPublicKeyBase64,
        "dogfood-platform-jwt",
        "RSA",
        KeyRole.PLATFORM);
    TaktXClient.publishSigningKey(
        defaultNamespaceProperties,
        POLICY_WRITER_KEY_ID,
        policyWriterPublicKeyBase64,
        "dogfood-policy-writer",
        "Ed25519",
        KeyRole.PLATFORM);
    TaktXClient.publishSigningKey(
        defaultNamespaceProperties,
        ROGUE_WRITER_KEY_ID,
        rogueWriterPublicKeyBase64,
        "dogfood-random-client",
        "Ed25519",
        KeyRole.CLIENT);

    TaktXClient.clearNamespaceSecurityPolicy(platformWriterProperties(DEFAULT_NAMESPACE));
  }

  @AfterEach
  void tearDownClientsAndPolicy() {
    List<TaktXClient> clients = new ArrayList<>(startedClients);
    startedClients.clear();
    for (TaktXClient client : clients.reversed()) {
      try {
        client.stop();
      } catch (Exception ignored) {
        // Best-effort cleanup — the next test creates fresh clients and unique consumer groups.
      }
    }
    TaktXClient.clearNamespaceSecurityPolicy(platformWriterProperties(DEFAULT_NAMESPACE));
  }

  @Test
  void communityOpenNamespace_allowsPublicClientRuntimeWithoutSecurityBootstrap() throws Exception {
    TaktXClient client =
        startClient(
            baseProperties(DEFAULT_NAMESPACE),
            participantDescriptor(
                "dogfood-open-client",
                Set.of(
                    ParticipantCapability.PROTECTED_RUNTIME_PARTICIPANT,
                    ParticipantCapability.SECURITY_OBSERVER),
                "dogfood-open-client"));

    awaitNoPolicy(client);

    Queue<InstanceUpdateRecord> updates = new ConcurrentLinkedQueue<>();
    client
        .runtime()
        .registerInstanceUpdateConsumer(
            "dogfood-open-updates-" + UUID.randomUUID(), updates::addAll);

    deployProcessAndAwaitAvailability(client, TASK_SINGLE_BPMN, OPEN_PROCESS_ID);

    UUID instanceId = client.runtime().startProcess(OPEN_PROCESS_ID, VariablesDTO.empty());
    awaitProcessCompleted(updates, instanceId);

    ObservedPolicySnapshot observedPolicy = client.observability().getObservedPolicySnapshot();
    SecurityPostureSnapshot posture = client.observability().getPostureSnapshot();
    assertThat(observedPolicy).isEqualTo(ObservedPolicySnapshot.empty());
    assertThat(posture.hasEffectivePolicy()).isFalse();
  }

  @Test
  void securedNamespace_rejectsRoguePolicyMutation_blocksUnauthorizedStart_and_allowsAuthorizedRuntimeAndSignedWorkerCompletion()
      throws Exception {
    long securedPolicyVersion = nextPolicyVersion();

    TaktXClient observer =
        startClient(
            baseProperties(DEFAULT_NAMESPACE),
            participantDescriptor(
                "dogfood-observer",
                Set.of(ParticipantCapability.SECURITY_OBSERVER),
                "dogfood-observer"));
    TaktXClient publisher =
        startClient(
            platformWriterProperties(DEFAULT_NAMESPACE),
            participantDescriptor(
                "dogfood-console",
                Set.of(
                    ParticipantCapability.AUTHORITATIVE_POLICY_PUBLISHER,
                    ParticipantCapability.SECURITY_OBSERVER),
                "console"));
    TaktXClient runtimeClient =
        startClient(
            baseProperties(DEFAULT_NAMESPACE),
            participantDescriptor(
                "dogfood-runtime",
                Set.of(
                    ParticipantCapability.PROTECTED_RUNTIME_PARTICIPANT,
                    ParticipantCapability.SECURITY_OBSERVER),
                "orders-console"));
    TaktXClient workerClient =
        startClient(
            baseProperties(DEFAULT_NAMESPACE),
            participantDescriptor(
                "dogfood-worker",
                Set.of(
                    ParticipantCapability.PROTECTED_RUNTIME_PARTICIPANT,
                    ParticipantCapability.SECURITY_OBSERVER),
                "worker-service"));

    awaitNoPolicy(observer);

    Queue<InstanceUpdateRecord> updates = new ConcurrentLinkedQueue<>();
    runtimeClient
        .runtime()
        .registerInstanceUpdateConsumer(
            "dogfood-secured-updates-" + UUID.randomUUID(), updates::addAll);
    deployProcessAndAwaitAvailability(runtimeClient, SERVICE_TASK_BPMN, SERVICE_PROCESS_ID);

    Queue<ExternalTaskTriggerDTO> triggers = new ConcurrentLinkedQueue<>();
    String externalTaskTopic = workerClient.workers().requestExternalTaskTopic(SERVICE_TASK_TYPE);
    awaitTopicExists(externalTaskTopic);
    workerClient
        .workers()
        .registerExternalTaskConsumer(
            new ExternalTaskTriggerConsumer() {
              @Override
              public Set<String> getJobIds() {
                return Set.of(SERVICE_TASK_TYPE);
              }

              @Override
              public void acceptBatch(List<ExternalTaskTriggerDTO> batch) {
                triggers.addAll(batch);
              }
            },
            "dogfood-worker-" + UUID.randomUUID());

    TaktXClient.publishNamespaceSecurityPolicy(
        rogueWriterProperties(DEFAULT_NAMESPACE), requestedSecuredPolicy(securedPolicyVersion - 1));

    SecurityEventDTO rejectionEvent =
        observer
            .observability()
            .awaitSecurityEvent(
                event ->
                    event.getEventType() == SecurityEventType.CONTROL_PLANE_MUTATION_REJECTED
                        && event.getMessage() != null
                        && event.getMessage().contains(ROGUE_WRITER_KEY_ID)
                        && event.getMessage().contains("required role PLATFORM"),
                Duration.ofSeconds(30));
    assertThat(rejectionEvent.getMessage()).contains("required role PLATFORM");
    awaitNoPolicy(observer);

    ObservedPolicySnapshot observedPolicy =
        publishPolicyAndAwaitObserved(
            publisher, observer, activeSecuredPolicy(securedPolicyVersion), Duration.ofSeconds(30));
    runtimeClient
        .observability()
        .awaitObservedPolicy(
            snapshot ->
                snapshot.hasAuthoritativePolicy()
                    && Long.valueOf(securedPolicyVersion).equals(snapshot.effectivePolicyVersion()),
            Duration.ofSeconds(30));
    workerClient
        .observability()
        .awaitObservedPolicy(
            snapshot ->
                snapshot.hasAuthoritativePolicy()
                    && Long.valueOf(securedPolicyVersion).equals(snapshot.effectivePolicyVersion()),
            Duration.ofSeconds(30));

    SecurityPostureSnapshot posture =
        observer
            .observability()
            .awaitPostureSnapshot(
                snapshot ->
                    snapshot.hasEffectivePolicy()
                        && snapshot.recentSecurityEvents().stream()
                            .anyMatch(
                                event ->
                                    event.getEventType()
                                            == SecurityEventType.CONTROL_PLANE_MUTATION_REJECTED
                                        && event.getMessage() != null
                                        && event.getMessage().contains(ROGUE_WRITER_KEY_ID)),
                Duration.ofSeconds(30));

    assertThat(observedPolicy.effectiveMode()).isEqualTo(SecurityMode.COMMUNITY_SECURED);
    assertThat(posture.recentSecurityEvents())
        .extracting(SecurityEventDTO::getEventType)
        .contains(SecurityEventType.CONTROL_PLANE_MUTATION_REJECTED);

    assertThatThrownBy(() -> runtimeClient.runtime().startProcess(SERVICE_PROCESS_ID, VariablesDTO.empty()))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("JWT authorization for start commands");

    UUID instanceId =
        runtimeClient
            .runtime()
            .startProcess(
                SERVICE_PROCESS_ID,
                -1,
                VariablesDTO.empty(),
                jwt("START", SERVICE_PROCESS_ID, -1));

    ExternalTaskTriggerDTO trigger = awaitExternalTaskTrigger(triggers, instanceId);
    workerClient
        .runtime()
        .completeExternalTask(
            trigger.getProcessInstanceId(), trigger.getElementInstanceIdPath(), VariablesDTO.empty());

    awaitProcessCompleted(updates, instanceId);
  }

  @Test
  void securityObservability_isNamespaceScoped() {
    long securedPolicyVersion = nextPolicyVersion();

    TaktXClient defaultObserver =
        startClient(
            baseProperties(DEFAULT_NAMESPACE),
            participantDescriptor(
                "dogfood-default-observer",
                Set.of(ParticipantCapability.SECURITY_OBSERVER),
                "dogfood-default-observer"));
    TaktXClient isolatedObserver =
        startClient(
            baseProperties(ISOLATED_NAMESPACE),
            participantDescriptor(
                "dogfood-isolated-observer",
                Set.of(ParticipantCapability.SECURITY_OBSERVER),
                "dogfood-isolated-observer"));
    TaktXClient publisher =
        startClient(
            platformWriterProperties(DEFAULT_NAMESPACE),
            participantDescriptor(
                "dogfood-default-console",
                Set.of(
                    ParticipantCapability.AUTHORITATIVE_POLICY_PUBLISHER,
                    ParticipantCapability.SECURITY_OBSERVER),
                "console"));

    awaitNoPolicy(defaultObserver);
    assertThat(isolatedObserver.observability().getObservedPolicySnapshot())
        .isEqualTo(ObservedPolicySnapshot.empty());

    publishPolicyAndAwaitObserved(
        publisher,
        defaultObserver,
        activeSecuredPolicy(securedPolicyVersion),
        Duration.ofSeconds(30));

    await()
        .during(Duration.ofSeconds(2))
        .atMost(Duration.ofSeconds(5))
        .untilAsserted(
            () -> {
              assertThat(isolatedObserver.observability().getObservedPolicySnapshot())
                  .isEqualTo(ObservedPolicySnapshot.empty());
              assertThat(isolatedObserver.observability().getPostureSnapshot().hasEffectivePolicy())
                  .isFalse();
              assertThat(isolatedObserver.observability().getRecentSecurityEvents()).isEmpty();
            });
  }

  private TaktXClient startClient(Properties properties, SecurityParticipantDescriptor descriptor) {
    TaktXClient client =
        TaktXClient.newClientBuilder()
            .withProperties(properties)
            .withParticipantDescriptor(descriptor)
            .build();
    client.start();
    startedClients.add(client);
    return client;
  }

  private static void deployProcessAndAwaitAvailability(
      TaktXClient client, String bpmnXml, String processDefinitionId) throws Exception {
    var parsedDefinitions =
        client
            .runtime()
            .deployProcessDefinition(
                new ByteArrayInputStream(bpmnXml.getBytes(StandardCharsets.UTF_8)));
    await()
        .atMost(Duration.ofSeconds(30))
        .pollInterval(Duration.ofMillis(100))
        .until(
            () ->
                client
                    .runtime()
                    .getProcessDefinitionByHash(
                        processDefinitionId, parsedDefinitions.getDefinitionsKey().getHash())
                    .isPresent());
  }

  private static void awaitNoPolicy(TaktXClient client) {
    client
        .observability()
        .awaitObservedPolicy(snapshot -> !snapshot.hasAuthoritativePolicy(), Duration.ofSeconds(30));
  }

  private static ObservedPolicySnapshot publishPolicyAndAwaitObserved(
      TaktXClient publisher,
      TaktXClient observer,
      NamespaceSecurityPolicyDTO policy,
      Duration timeout) {
    AtomicReference<ObservedPolicySnapshot> observedPolicy = new AtomicReference<>();
    await()
        .atMost(timeout)
        .pollInterval(Duration.ofMillis(200))
        .ignoreExceptions()
        .until(
            () -> {
              publisher.security().publishNamespaceSecurityPolicy(policy);
              ObservedPolicySnapshot snapshot = observer.observability().getObservedPolicySnapshot();
              if (snapshot.hasAuthoritativePolicy()
                  && Long.valueOf(policy.getDesiredPolicyVersion())
                      .equals(snapshot.effectivePolicyVersion())) {
                observedPolicy.set(snapshot);
                return true;
              }
              return false;
            });
    return observedPolicy.get();
  }

  private static ExternalTaskTriggerDTO awaitExternalTaskTrigger(
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

  private static void awaitProcessCompleted(Queue<InstanceUpdateRecord> updates, UUID processInstanceId) {
    await()
        .atMost(Duration.ofSeconds(30))
        .pollInterval(Duration.ofMillis(100))
        .until(
            () ->
                updates.stream()
                    .filter(record -> processInstanceId.equals(record.getProcessInstanceId()))
                    .map(InstanceUpdateRecord::getUpdate)
                    .filter(ProcessInstanceUpdateDTO.class::isInstance)
                    .map(ProcessInstanceUpdateDTO.class::cast)
                    .anyMatch(
                        update ->
                            update.getScope() != null
                                && update.getScope().getState() == ExecutionState.COMPLETED));
  }

  private static void awaitTopicExists(String topicName) {
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

  private static NamespaceSecurityPolicyDTO requestedSecuredPolicy(long version) {
    return NamespaceSecurityPolicyDTO.builder()
        .mode(SecurityMode.COMMUNITY_SECURED)
        .activationState(SecurityActivationState.REQUESTED)
        .desiredPolicyVersion(version)
        .requiredSigning(
            RequiredSigningDTO.builder().clientCommands(true).workerResponses(true).build())
        .requiredAuthorization(
            RequiredAuthorizationDTO.builder()
                .startCommands(true)
                .externalTaskCompletion(true)
                .build())
        .build();
  }

  private static NamespaceSecurityPolicyDTO activeSecuredPolicy(long version) {
    NamespaceSecurityPolicyDTO requestedPolicy = requestedSecuredPolicy(version);
    return requestedPolicy.toBuilder()
        .activationState(SecurityActivationState.ACTIVE)
        .activePolicyVersion(version)
        .activePolicyHash(requestedPolicy.getDesiredPolicyHash())
        .build();
  }

  private static long nextPolicyVersion() {
    return POLICY_VERSIONS.incrementAndGet();
  }

  private static SecurityParticipantDescriptor participantDescriptor(
      String participantId, Set<ParticipantCapability> capabilities, String componentType) {
    return new SecurityParticipantDescriptor(
        participantId, ParticipantKind.CLIENT, capabilities, componentType);
  }

  private static String jwt(String action, String processDefinitionId, int version) {
    JwtBuilder builder =
        Jwts.builder()
            .header()
            .keyId(SecurityTestConfigResource.PLATFORM_KID)
            .and()
            .subject("dogfood-user")
            .issuer(ISSUER)
            .claim("action", action)
            .claim("namespaceId", UUID.randomUUID().toString())
            .claim("auditId", UUID.randomUUID().toString())
            .expiration(Date.from(Instant.now().plusSeconds(300)));
    if (processDefinitionId != null && !processDefinitionId.isBlank()) {
      builder.claim("processDefinitionId", processDefinitionId);
    }
    if (version != 0) {
      builder.claim("version", version);
    }
    return builder.signWith(SecurityTestConfigResource.rsaPrivateKey).compact();
  }

  private static Properties baseProperties(String namespace) {
    Properties properties = new Properties();
    properties.setProperty("bootstrap.servers", bootstrapServers);
    properties.setProperty("taktx.engine.tenant-id", TENANT);
    properties.setProperty("taktx.engine.namespace", namespace);
    return properties;
  }

  private static Properties platformWriterProperties(String namespace) {
    Properties properties = baseProperties(namespace);
    properties.setProperty("taktx.signing.key-id", POLICY_WRITER_KEY_ID);
    properties.setProperty("taktx.signing.private-key", policyWriterPrivateKeyBase64);
    properties.setProperty("taktx.signing.public-key", policyWriterPublicKeyBase64);
    return properties;
  }

  private static Properties rogueWriterProperties(String namespace) {
    Properties properties = baseProperties(namespace);
    properties.setProperty("taktx.signing.key-id", ROGUE_WRITER_KEY_ID);
    properties.setProperty("taktx.signing.private-key", rogueWriterPrivateKeyBase64);
    properties.setProperty("taktx.signing.public-key", rogueWriterPublicKeyBase64);
    return properties;
  }
}




