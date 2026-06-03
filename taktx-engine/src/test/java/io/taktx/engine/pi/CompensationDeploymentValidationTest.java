/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */

package io.taktx.engine.pi;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.taktx.dto.BoundaryEventDTO;
import io.taktx.dto.CompensationEventDefinitionDTO;
import io.taktx.dto.DefinitionsKey;
import io.taktx.dto.FlowElementDTO;
import io.taktx.dto.FlowElementsDTO;
import io.taktx.dto.IntermediateThrowEventDTO;
import io.taktx.dto.ParsedDefinitionsDTO;
import io.taktx.dto.ProcessDTO;
import io.taktx.dto.ServiceTaskDTO;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class CompensationDeploymentValidationTest {

  private DefinitionMapper definitionMapper;

  @BeforeEach
  void setUp() {
    definitionMapper = new DefinitionMapper(Mappers.getMapper(DtoMapper.class));
  }

  // ── helpers ──────────────────────────────────────────────────────────────

  private static ServiceTaskDTO task(String id) {
    return new ServiceTaskDTO(
        id, null, id, id, null, Set.of(), Set.of(), null, null, Map.of(), null);
  }

  private static ServiceTaskDTO handlerTask(String id) {
    ServiceTaskDTO dto =
        new ServiceTaskDTO(id, null, id, id, null, Set.of(), Set.of(), null, null, Map.of(), null);
    dto.setForCompensation(true);
    return dto;
  }

  private static BoundaryEventDTO compensationBoundary(
      String id, String attachedTo, String handlerId) {
    return new BoundaryEventDTO(
        id,
        null,
        null,
        Set.of(),
        Set.of(),
        Set.of(new CompensationEventDefinitionDTO(id + "-ced", null)),
        attachedTo,
        false,
        null,
        handlerId);
  }

  private static IntermediateThrowEventDTO compensationThrow(String id, String activityRef) {
    return new IntermediateThrowEventDTO(
        id,
        null,
        null,
        Set.of(),
        Set.of(),
        null,
        Set.of(new CompensationEventDefinitionDTO(id + "-ced", activityRef)));
  }

  private ParsedDefinitionsDTO model(FlowElementDTO... elements) {
    Map<String, FlowElementDTO> map = new HashMap<>();
    for (FlowElementDTO e : elements) {
      map.put(e.getId(), e);
    }
    return ParsedDefinitionsDTO.builder()
        .definitionsKey(new DefinitionsKey("test-proc", "hash"))
        .rootProcess(new ProcessDTO("test-proc", null, null, new FlowElementsDTO(map)))
        .messages(Map.of())
        .errors(Map.of())
        .escalations(Map.of())
        .signals(Map.of())
        .build();
  }

  // ── valid model ───────────────────────────────────────────────────────────

  @Test
  void validModel_noException() {
    assertThatCode(
            () ->
                definitionMapper.getFlowElements(
                    model(
                        task("task-a"),
                        compensationBoundary("compensate-task-a", "task-a", "undo-task-a"),
                        handlerTask("undo-task-a"))))
        .doesNotThrowAnyException();
  }

  // ── Req §16: missing handler association ──────────────────────────────────

  @Test
  void boundaryEventWithoutHandlerId_throwsOnDeploy() {
    // compensationHandlerId is null — no association was modelled
    assertThatThrownBy(
            () ->
                definitionMapper.getFlowElements(
                    model(
                        task("task-a"),
                        compensationBoundary("compensate-task-a", "task-a", null),
                        handlerTask("undo-task-a"))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("compensate-task-a")
        .hasMessageContaining("no associated handler");
  }

  // ── Req §16: handler not marked isForCompensation ─────────────────────────

  @Test
  void handlerNotMarkedForCompensation_throwsOnDeploy() {
    // undo-task-a exists but isForCompensation = false
    assertThatThrownBy(
            () ->
                definitionMapper.getFlowElements(
                    model(
                        task("task-a"),
                        compensationBoundary("compensate-task-a", "task-a", "undo-task-a"),
                        task("undo-task-a")))) // plain task, NOT a compensation handler
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("undo-task-a")
        .hasMessageContaining("isForCompensation");
  }

  // ── Req §16: activityRef to nonexistent element ───────────────────────────

  @Test
  void activityRefToUnknownElement_throwsOnDeploy() {
    assertThatThrownBy(
            () ->
                definitionMapper.getFlowElements(
                    model(
                        task("task-a"),
                        compensationBoundary("compensate-task-a", "task-a", "undo-task-a"),
                        handlerTask("undo-task-a"),
                        compensationThrow("throw-1", "nonexistent-task"))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("nonexistent-task");
  }

  // ── Req §16: activityRef to element without compensation boundary ──────────

  @Test
  void activityRefToElementWithoutCompensationBoundary_throwsOnDeploy() {
    // task-b has no compensation boundary event
    assertThatThrownBy(
            () ->
                definitionMapper.getFlowElements(
                    model(
                        task("task-a"),
                        compensationBoundary("compensate-task-a", "task-a", "undo-task-a"),
                        handlerTask("undo-task-a"),
                        task("task-b"),
                        compensationThrow("throw-1", "task-b"))))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("task-b")
        .hasMessageContaining("compensation boundary event");
  }

  // ── Req §16: activityRef to non-activity element ─────────────────────────

  @Test
  void activityRefToEventDefinition_throwsOnDeploy() {
    // activityRef points to the throw event itself — not an activity
    assertThatThrownBy(
            () ->
                definitionMapper.getFlowElements(
                    model(
                        task("task-a"),
                        compensationBoundary("compensate-task-a", "task-a", "undo-task-a"),
                        handlerTask("undo-task-a"),
                        compensationThrow(
                            "throw-1", "throw-1")))) // self-reference, not an Activity
        .isInstanceOf(IllegalStateException.class);
  }
}
