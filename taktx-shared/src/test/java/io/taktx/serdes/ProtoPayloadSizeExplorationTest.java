/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.serdes;

import static org.assertj.core.api.Assertions.assertThat;

import io.taktx.proto.ProcessDefinitionKeyMessage;
import io.taktx.proto.ProcessInstanceMessage;
import io.taktx.proto.ProcessInstanceTriggerEnvelope;
import io.taktx.proto.StartCommandMessage;
import io.taktx.proto.UserTaskTriggerMessage;
import io.taktx.proto.Uuid;
import io.taktx.proto.VarMap;
import io.taktx.variables.Variables;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Informational size exploration for often-used current protobuf message families.
 *
 * <p>This does not compare against historical CBOR for every DTO family. Instead it reports where
 * protobuf gets leverage from omitting absent fields and where explicit null/empty/default values
 * still consume bytes because they are intentionally present on the wire.
 */
class ProtoPayloadSizeExplorationTest {

  @Test
  void oftenUsedEnvelopeSizes_arePrintedForInspection() {
    int processInstanceTriggerSize =
        GoldenFixtureSamples.processInstanceTriggerFixture().message().getSerializedSize();
    int instanceUpdateSize =
        GoldenFixtureSamples.instanceUpdateFixture().message().getSerializedSize();
    int flowNodeInstanceSize =
        GoldenFixtureSamples.flowNodeInstanceFixture().message().getSerializedSize();
    int processInstanceSize =
        GoldenFixtureSamples.processInstanceFixture().message().getSerializedSize();
    int userTaskTriggerSize =
        GoldenFixtureSamples.userTaskTriggerFixture().message().getSerializedSize();

    System.out.printf(
        "Proto payload size report [full fixtures]: process-instance-trigger=%dB instance-update=%dB flow-node-instance=%dB process-instance=%dB user-task-trigger=%dB%n",
        processInstanceTriggerSize,
        instanceUpdateSize,
        flowNodeInstanceSize,
        processInstanceSize,
        userTaskTriggerSize);

    assertThat(processInstanceTriggerSize).isPositive();
    assertThat(instanceUpdateSize).isPositive();
    assertThat(flowNodeInstanceSize).isPositive();
    assertThat(processInstanceSize).isPositive();
    assertThat(userTaskTriggerSize).isPositive();
  }

  @Test
  void sparseMessages_areMuchSmallerThanFullFixturesBecauseAbsentFieldsAreOmitted() {
    ProcessInstanceTriggerEnvelope fullTrigger =
        GoldenFixtureSamples.processInstanceTriggerFixture().message();
    ProcessInstanceMessage fullProcessInstance =
        GoldenFixtureSamples.processInstanceFixture().message();
    UserTaskTriggerMessage fullUserTask = GoldenFixtureSamples.userTaskTriggerFixture().message();

    ProcessInstanceTriggerEnvelope sparseTrigger =
        ProcessInstanceTriggerEnvelope.newBuilder()
            .setStart(
                StartCommandMessage.newBuilder()
                    .setProcessInstanceId(uuid("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa"))
                    .setProcessDefinitionKey(processDefinitionKey("orders"))
                    .build())
            .build();

    ProcessInstanceMessage sparseProcessInstance =
        ProcessInstanceMessage.newBuilder()
            .setProcessInstanceId(uuid("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb"))
            .setProcessDefinitionKey(processDefinitionKey("orders"))
            .build();

    UserTaskTriggerMessage sparseUserTask =
        UserTaskTriggerMessage.newBuilder()
            .setProcessInstanceId(uuid("cccccccc-cccc-cccc-cccc-cccccccccccc"))
            .setProcessDefinitionKey(processDefinitionKey("review"))
            .setUserTaskId("approve-order")
            .build();

    System.out.printf(
        "Proto payload size report [sparse-vs-full]: trigger=%dB/%dB process-instance=%dB/%dB user-task=%dB/%dB%n",
        sparseTrigger.getSerializedSize(),
        fullTrigger.getSerializedSize(),
        sparseProcessInstance.getSerializedSize(),
        fullProcessInstance.getSerializedSize(),
        sparseUserTask.getSerializedSize(),
        fullUserTask.getSerializedSize());

    assertThat(sparseTrigger.getSerializedSize()).isLessThan(fullTrigger.getSerializedSize());
    assertThat(sparseProcessInstance.getSerializedSize())
        .isLessThan(fullProcessInstance.getSerializedSize());
    assertThat(sparseUserTask.getSerializedSize()).isLessThan(fullUserTask.getSerializedSize());
  }

  @Test
  void absentFields_areCheaperThanExplicitNullOrEmptyOrDefaultVariableValues() {
    VarMap absent = VarMap.newBuilder().build();
    int nullValueSize = Variables.nullValue().getSerializedSize();
    int emptyStringSize = Variables.of("").getSerializedSize();
    int falseValueSize = Variables.of(false).getSerializedSize();
    int zeroLongSize = Variables.of(0L).getSerializedSize();

    System.out.printf(
        "Proto variable presence report: absent-varmap=%dB null=%dB empty-string=%dB false=%dB zero-long=%dB%n",
        absent.getSerializedSize(), nullValueSize, emptyStringSize, falseValueSize, zeroLongSize);

    assertThat(absent.getSerializedSize()).isZero();
    assertThat(nullValueSize).isPositive();
    assertThat(emptyStringSize).isPositive();
    assertThat(falseValueSize).isPositive();
    assertThat(zeroLongSize).isPositive();
  }

  private static Uuid uuid(String raw) {
    UUID uuid = UUID.fromString(raw);
    return Uuid.newBuilder()
        .setHigh(uuid.getMostSignificantBits())
        .setLow(uuid.getLeastSignificantBits())
        .build();
  }

  private static ProcessDefinitionKeyMessage processDefinitionKey(String id) {
    return ProcessDefinitionKeyMessage.newBuilder()
        .setProcessDefinitionId(id)
        .setVersion(1)
        .build();
  }
}
