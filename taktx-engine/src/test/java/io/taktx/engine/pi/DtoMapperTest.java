/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.pi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import io.taktx.dto.InputOutputMappingDTO;
import io.taktx.dto.IoVariableMappingDTO;
import io.taktx.dto.LinkEventDefinitionDTO;
import io.taktx.dto.StartEventDTO;
import io.taktx.engine.pd.model.FlowElement;
import io.taktx.engine.pd.model.StartEvent;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class DtoMapperTest {

  @Test
  void testMap() {
    DtoMapper dtoMapper = Mappers.getMapper(DtoMapper.class);
    LinkEventDefinitionDTO eventDefinition =
        new LinkEventDefinitionDTO("eventDefinitionId", "name");
    InputOutputMappingDTO ioMapping =
        new InputOutputMappingDTO(
            Set.of(new IoVariableMappingDTO("inputSource", "inputTarget")),
            Set.of(new IoVariableMappingDTO("outputSource", "outputTarget")));
    StartEventDTO startEventDTO =
        new StartEventDTO(
            "id",
            "parentId",
            Set.of("incoming"),
            Set.of("outgoing"),
            Set.of(eventDefinition),
            ioMapping,
            true);
    FlowElement map = dtoMapper.getFlowElement(startEventDTO);

    assertNotNull(map);
    assertThat(map).isInstanceOf(StartEvent.class);
    StartEvent startEvent2 = (StartEvent) map;

    assertThat(startEvent2.getId()).isEqualTo("id");
    assertThat(startEvent2.getIncoming()).containsExactly("incoming");
    assertThat(startEvent2.getOutgoing()).containsExactly("outgoing");
    assertThat(startEvent2.getEventDefinitions().iterator().next().getId())
        .isEqualTo("eventDefinitionId");
    assertThat(startEvent2.isInterrupting()).isTrue();
  }
}
