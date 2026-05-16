/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/**
 * Guards the contract that businessKey and tags are only present on the initial start update
 * (6-arg ProcessInstanceDTO constructor) and are explicitly absent on every subsequent
 * state-change update (4-arg ProcessInstanceDTO constructor).
 */
class ProcessInstanceUpdateDTOBusinessMetadataTest {

  // ── helpers ────────────────────────────────────────────────────────────────

  private static ProcessInstanceDTO buildDto(String businessKey, Set<String> tags) {
    return new ProcessInstanceDTO(
        UUID.randomUUID(),
        null,
        null,
        null,
        new ProcessDefinitionKey("my-process", -1),
        false,
        Set.of(),
        null,
        businessKey,
        tags);
  }

  // ── start update — businessKey and tags must be populated ─────────────────

  @Test
  void startUpdate_businessKeyAndTags_arePresentOnInitialUpdate() {
    ProcessInstanceDTO dto = buildDto("order-42", Set.of("region.eu", "priority.high"));

    ProcessInstanceUpdateDTO update =
        new ProcessInstanceUpdateDTO(dto, VariablesDTO.empty(), 1_000L, null, "order-42", Set.of("region.eu", "priority.high"));

    assertThat(update.getBusinessKey()).isEqualTo("order-42");
    assertThat(update.getTags()).containsExactlyInAnyOrder("region.eu", "priority.high");
  }

  @Test
  void startUpdate_nullBusinessKey_isNullInInitialUpdate() {
    ProcessInstanceDTO dto = buildDto(null, Set.of("env.prod"));

    ProcessInstanceUpdateDTO update =
        new ProcessInstanceUpdateDTO(dto, VariablesDTO.empty(), 1_000L, null, null, Set.of("env.prod"));

    assertThat(update.getBusinessKey()).isNull();
    assertThat(update.getTags()).containsExactly("env.prod");
  }

  @Test
  void startUpdate_emptyTags_isEmptySetInInitialUpdate() {
    ProcessInstanceDTO dto = buildDto("key-1", Set.of());

    ProcessInstanceUpdateDTO update =
        new ProcessInstanceUpdateDTO(dto, VariablesDTO.empty(), 1_000L, null, "key-1", Set.of());

    assertThat(update.getBusinessKey()).isEqualTo("key-1");
    assertThat(update.getTags()).isEmpty();
  }

  @Test
  void startUpdate_nullTags_normalisedToEmptySet() {
    ProcessInstanceDTO dto = buildDto("key-2", null);

    ProcessInstanceUpdateDTO update =
        new ProcessInstanceUpdateDTO(dto, VariablesDTO.empty(), 1_000L, null, "key-2", null);

    assertThat(update.getBusinessKey()).isEqualTo("key-2");
    assertThat(update.getTags()).isEmpty();
  }

  // ── subsequent update — businessKey and tags must be absent ───────────────

  @Test
  void subsequentUpdate_businessKeyAndTags_areAbsent() {
    // Even when the stored ProcessInstanceDTO has businessKey/tags populated,
    // the 4-arg delegate constructor must not propagate them.
    ProcessInstanceDTO dto = buildDto("order-42", Set.of("region.eu", "priority.high"));

    ProcessInstanceUpdateDTO update =
        new ProcessInstanceUpdateDTO(dto, VariablesDTO.empty(), null, null);

    assertThat(update.getBusinessKey())
        .as("businessKey must be null on state-change updates")
        .isNull();
    assertThat(update.getTags())
        .as("tags must be empty on state-change updates")
        .isEmpty();
  }

  @Test
  void subsequentUpdate_neverExposesBusinessMetadata_regardlessOfStoredDto() {
    // Guard that even end/abort updates with start+end timestamps don't leak metadata.
    ProcessInstanceDTO dto = buildDto("invoice-99", Set.of("billing"));
    long endTime = 9_000L;

    ProcessInstanceUpdateDTO update =
        new ProcessInstanceUpdateDTO(dto, VariablesDTO.empty(), null, endTime);

    assertThat(update.getBusinessKey()).isNull();
    assertThat(update.getTags()).isEmpty();
    assertThat(update.getProcessEndTime()).isEqualTo(endTime);
  }

  // ── symmetry: processDefinitionKey and other fields still propagate ────────

  @Test
  void subsequentUpdate_otherFields_areStillPropagated() {
    ProcessDefinitionKey key = new ProcessDefinitionKey("billing-process", 3);
    ProcessInstanceDTO dto =
        new ProcessInstanceDTO(
            UUID.randomUUID(), null, null, null, key, false, Set.of(), null, "some-bk", Set.of("t1"));

    ProcessInstanceUpdateDTO update =
        new ProcessInstanceUpdateDTO(dto, VariablesDTO.empty(), null, 5_000L);

    // Non-metadata fields must still be copied correctly.
    assertThat(update.getProcessDefinitionKey()).isEqualTo(key);
    assertThat(update.getProcessEndTime()).isEqualTo(5_000L);
    // But business metadata must be absent.
    assertThat(update.getBusinessKey()).isNull();
    assertThat(update.getTags()).isEmpty();
  }
}

