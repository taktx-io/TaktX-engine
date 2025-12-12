/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.pi.processor;

import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.node.TextNode;
import io.taktx.engine.feel.FeelExpressionHandler;
import io.taktx.engine.pd.model.InputOutputMapping;
import io.taktx.engine.pd.model.IoVariableMapping;
import io.taktx.engine.pd.model.WithIoMapping;
import io.taktx.engine.pi.model.VariableScope;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class IoMappingProcessorTest {

  @InjectMocks private IoMappingProcessor processor;

  @Mock private FeelExpressionHandler feelExpressionHandler;
  @Mock private WithIoMapping element;
  @Mock private InputOutputMapping ioMapping;
  @Mock private VariableScope variableScope;

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

    verify(variableScope).put("targetVar1", new TextNode("value1"));
    verify(variableScope).put("targetVar2", new TextNode("value2"));
  }

  @Test
  void processOutputMappings_shouldHandleEmptyMappings() {
    when(element.getIoMapping()).thenReturn(ioMapping);
    when(ioMapping.getOutputMappings()).thenReturn(Set.of());

    processor.processOutputMappings(element, variableScope);

    verify(variableScope, never()).put(anyString(), any());
  }

  @Test
  void addInputVariables_shouldAddVariablesFromInputMappings() {
    IoVariableMapping mapping1 = new IoVariableMapping("sourceExpr1", "targetVar1");

    when(element.getIoMapping()).thenReturn(ioMapping);
    when(ioMapping.getInputMappings()).thenReturn(Set.of(mapping1));
    when(feelExpressionHandler.processFeelExpression("sourceExpr1", variableScope))
        .thenReturn(new TextNode("value1"));

    processor.addInputVariables(element, variableScope);

    verify(variableScope).put("targetVar1", new TextNode("value1"));
  }

  @Test
  void addInputVariables_shouldReturnEarlyWhenMappingsAreEmpty() {
    when(element.getIoMapping()).thenReturn(ioMapping);
    when(ioMapping.getInputMappings()).thenReturn(Set.of());

    processor.addInputVariables(element, variableScope);

    verify(feelExpressionHandler, never()).processFeelExpression(anyString(), any());
    verify(variableScope, never()).put(anyString(), any());
  }

  @Test
  void addVariables_shouldProcessAllMappings() {
    IoVariableMapping mapping1 = new IoVariableMapping("source1", "target1");
    IoVariableMapping mapping2 = new IoVariableMapping("source2", "target2");
    IoVariableMapping mapping3 = new IoVariableMapping("source3", "target3");

    when(feelExpressionHandler.processFeelExpression("source1", variableScope))
        .thenReturn(new TextNode("val1"));
    when(feelExpressionHandler.processFeelExpression("source2", variableScope))
        .thenReturn(new TextNode("val2"));
    when(feelExpressionHandler.processFeelExpression("source3", variableScope))
        .thenReturn(new TextNode("val3"));

    processor.addVariables(variableScope, Set.of(mapping1, mapping2, mapping3));

    verify(variableScope).put("target1", new TextNode("val1"));
    verify(variableScope).put("target2", new TextNode("val2"));
    verify(variableScope).put("target3", new TextNode("val3"));
  }

  @Test
  void addVariables_shouldNotProcessWhenMappingsAreEmpty() {
    processor.addVariables(variableScope, Set.of());

    verify(feelExpressionHandler, never()).processFeelExpression(anyString(), any());
    verify(variableScope, never()).put(anyString(), any());
  }
}
