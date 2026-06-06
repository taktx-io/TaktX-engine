/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.serdes;

import static org.assertj.core.api.Assertions.assertThat;

import io.taktx.dto.NamespaceSecurityPolicyDTO;
import io.taktx.dto.SecurityMode;
import org.junit.jupiter.api.Test;

class NamespaceSecurityPolicyProtoMapperTest {

  @Test
  void namespaceSecurityPolicy_roundTripsThroughProto() {
    NamespaceSecurityPolicyDTO dto =
        NamespaceSecurityPolicyDTO.builder().mode(SecurityMode.ANCHORED).build();

    assertThat(
            NamespaceSecurityPolicyProtoMapper.toDto(
                NamespaceSecurityPolicyProtoMapper.toProto(dto)))
        .isEqualTo(dto);
  }

  @Test
  void namespaceSecurityPolicy_mapsOpenMode() {
    NamespaceSecurityPolicyDTO dto =
        NamespaceSecurityPolicyProtoMapper.toDto(
            io.taktx.proto.NamespaceSecurityPolicyMessage.newBuilder()
                .setMode(io.taktx.proto.SecurityModeMessage.OPEN)
                .build());

    assertThat(dto.getMode()).isEqualTo(SecurityMode.OPEN);
  }
}
