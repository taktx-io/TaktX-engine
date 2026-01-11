/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.pi.processor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import io.taktx.engine.feel.FeelExpressionHandler;
import io.taktx.engine.pd.model.InputOutputMapping;
import io.taktx.engine.pd.model.IoVariableMapping;
import io.taktx.engine.pd.model.WithIoMapping;
import io.taktx.engine.pi.model.VariableScope;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IoMappingProcessorTest {

  @Spy private ObjectMapper objectMapper = new ObjectMapper();

  @Mock private FeelExpressionHandler feelExpressionHandler;
  @Mock private WithIoMapping element;
  @Mock private InputOutputMapping ioMapping;
  @Spy private VariableScope parentScope = new VariableScope(null, null, null, null);
  @Spy private VariableScope variableScope = new VariableScope(parentScope, null, null, null);

  private IoMappingProcessor processor;

  @BeforeEach
  void setUp() {
    processor = new IoMappingProcessor(feelExpressionHandler, objectMapper);
  }

  @Test
  void processOutputMappings_shouldAddVariablesFromOutputMappings() {
    IoVariableMapping mapping1 = new IoVariableMapping("sourceExpr1", "targetVar1");
    IoVariableMapping mapping2 = new IoVariableMapping("sourceExpr2", "targetVar2");

    when(element.getIoMapping()).thenReturn(ioMapping);
    when(ioMapping.getOutputMappings()).thenReturn(Set.of(mapping1, mapping2));
    when(feelExpressionHandler.processFeelExpression("sourceExpr1", variableScope))
        .thenReturn(new TextNode("value1"));
    when(feelExpressionHandler.processFeelExpression("sourceExpr2", variableScope))
        .thenReturn(new TextNode("value2"));

    processor.processOutputMappings(element, variableScope);

    assertThat(parentScope.getVariables()).hasSize(2);
    assertThat(parentScope.get("targetVar1").asText()).isEqualTo("value1");
    assertThat(parentScope.get("targetVar2").asText()).isEqualTo("value2");
  }

  @Test
  void processOutputMappings_shouldHandleEmptyMappings() {
    when(element.getIoMapping()).thenReturn(ioMapping);
    when(ioMapping.getOutputMappings()).thenReturn(Set.of());

    processor.processOutputMappings(element, variableScope);

    verify(variableScope, never()).put(anyString(), any());
  }

  @Test
  void processInputMappings_shouldReturnEarlyWhenMappingsAreEmpty() {
    when(element.getIoMapping()).thenReturn(ioMapping);
    when(ioMapping.getInputMappings()).thenReturn(Set.of());

    processor.processInputMappings(element, variableScope);

    verify(feelExpressionHandler, never()).processFeelExpression(anyString(), any());
    verify(variableScope, never()).put(anyString(), any());
  }
}
