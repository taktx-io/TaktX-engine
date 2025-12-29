/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.pi.processor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import io.taktx.engine.feel.FeelExpressionHandler;
import io.taktx.engine.pd.model.InputOutputMapping;
import io.taktx.engine.pd.model.IoVariableMapping;
import io.taktx.engine.pd.model.WithIoMapping;
import io.taktx.engine.pi.model.VariableScope;
import java.util.HashMap;
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
  @Mock private VariableScope variableScope;

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

  @Test
  void addVariables_shouldHandleNestedVariablePath() {
    // Given
    IoVariableMapping mapping = new IoVariableMapping("sourceExpr", "user.name");
    HashMap<String, JsonNode> variables = new HashMap<>();
    when(variableScope.getVariables()).thenReturn(variables);
    // Make put() actually update the HashMap
    doAnswer(
            invocation -> {
              String key = invocation.getArgument(0);
              JsonNode value = invocation.getArgument(1);
              variables.put(key, value);
              return null;
            })
        .when(variableScope)
        .put(anyString(), any(JsonNode.class));

    when(feelExpressionHandler.processFeelExpression("sourceExpr", variableScope))
        .thenReturn(new TextNode("John"));

    // When
    processor.addVariables(variableScope, Set.of(mapping));

    // Then
    assertTrue(variables.containsKey("user"));
    JsonNode userNode = variables.get("user");
    assertTrue(userNode.isObject());
    assertEquals("John", userNode.get("name").asText());
  }

  @Test
  void addVariables_shouldHandleDeepNestedPath() {
    // Given
    IoVariableMapping mapping = new IoVariableMapping("sourceExpr", "user.address.city");
    HashMap<String, JsonNode> variables = new HashMap<>();
    when(variableScope.getVariables()).thenReturn(variables);
    // Make put() actually update the HashMap
    doAnswer(
            invocation -> {
              String key = invocation.getArgument(0);
              JsonNode value = invocation.getArgument(1);
              variables.put(key, value);
              return null;
            })
        .when(variableScope)
        .put(anyString(), any(JsonNode.class));

    when(feelExpressionHandler.processFeelExpression("sourceExpr", variableScope))
        .thenReturn(new TextNode("New York"));

    // When
    processor.addVariables(variableScope, Set.of(mapping));

    // Then
    assertTrue(variables.containsKey("user"));
    JsonNode userNode = variables.get("user");
    assertTrue(userNode.isObject());
    assertTrue(userNode.get("address").isObject());
    assertEquals("New York", userNode.get("address").get("city").asText());
  }

  @Test
  void addVariables_shouldMergeWithExistingNestedObject() {
    // Given
    IoVariableMapping mapping = new IoVariableMapping("sourceExpr", "user.name");
    HashMap<String, JsonNode> variables = new HashMap<>();

    // Create existing user object with age
    ObjectNode existingUser = objectMapper.createObjectNode();
    existingUser.put("age", 30);
    variables.put("user", existingUser);

    when(variableScope.getVariables()).thenReturn(variables);
    // Make put() actually update the HashMap
    doAnswer(
            invocation -> {
              String key = invocation.getArgument(0);
              JsonNode value = invocation.getArgument(1);
              variables.put(key, value);
              return null;
            })
        .when(variableScope)
        .put(anyString(), any(JsonNode.class));

    when(feelExpressionHandler.processFeelExpression("sourceExpr", variableScope))
        .thenReturn(new TextNode("John"));

    // When
    processor.addVariables(variableScope, Set.of(mapping));

    // Then
    assertTrue(variables.containsKey("user"));
    JsonNode userNode = variables.get("user");
    assertTrue(userNode.isObject());
    assertEquals("John", userNode.get("name").asText());
    assertEquals(30, userNode.get("age").asInt());
  }

  @Test
  void addVariables_shouldReplaceNonObjectWithNestedObject() {
    // Given
    IoVariableMapping mapping = new IoVariableMapping("sourceExpr", "user.name");
    HashMap<String, JsonNode> variables = new HashMap<>();

    // Existing user is a string, not an object
    variables.put("user", new TextNode("someString"));

    when(variableScope.getVariables()).thenReturn(variables);
    // Make put() actually update the HashMap
    doAnswer(
            invocation -> {
              String key = invocation.getArgument(0);
              JsonNode value = invocation.getArgument(1);
              variables.put(key, value);
              return null;
            })
        .when(variableScope)
        .put(anyString(), any(JsonNode.class));

    when(feelExpressionHandler.processFeelExpression("sourceExpr", variableScope))
        .thenReturn(new TextNode("John"));

    // When
    processor.addVariables(variableScope, Set.of(mapping));

    // Then
    assertTrue(variables.containsKey("user"));
    JsonNode userNode = variables.get("user");
    assertTrue(userNode.isObject());
    assertEquals("John", userNode.get("name").asText());
  }

  @Test
  void addVariables_shouldHandleMultipleNestedPaths() {
    // Given
    IoVariableMapping mapping1 = new IoVariableMapping("expr1", "user.name");
    IoVariableMapping mapping2 = new IoVariableMapping("expr2", "user.age");
    HashMap<String, JsonNode> variables = new HashMap<>();

    when(variableScope.getVariables()).thenReturn(variables);
    // Make put() actually update the HashMap
    doAnswer(
            invocation -> {
              String key = invocation.getArgument(0);
              JsonNode value = invocation.getArgument(1);
              variables.put(key, value);
              return null;
            })
        .when(variableScope)
        .put(anyString(), any(JsonNode.class));

    when(feelExpressionHandler.processFeelExpression("expr1", variableScope))
        .thenReturn(new TextNode("John"));
    when(feelExpressionHandler.processFeelExpression("expr2", variableScope))
        .thenReturn(new IntNode(30));

    // When
    processor.addVariables(variableScope, Set.of(mapping1, mapping2));

    // Then
    assertTrue(variables.containsKey("user"));
    JsonNode userNode = variables.get("user");
    assertTrue(userNode.isObject());
    assertEquals("John", userNode.get("name").asText());
    assertEquals(30, userNode.get("age").asInt());
  }

  @Test
  void addVariables_shouldHandleMixedSimpleAndNestedPaths() {
    // Given
    IoVariableMapping simpleMapping = new IoVariableMapping("expr1", "simpleVar");
    IoVariableMapping nestedMapping = new IoVariableMapping("expr2", "user.name");
    HashMap<String, JsonNode> variables = new HashMap<>();

    when(variableScope.getVariables()).thenReturn(variables);
    when(feelExpressionHandler.processFeelExpression("expr1", variableScope))
        .thenReturn(new TextNode("simpleValue"));
    when(feelExpressionHandler.processFeelExpression("expr2", variableScope))
        .thenReturn(new TextNode("John"));

    // When
    processor.addVariables(variableScope, Set.of(simpleMapping, nestedMapping));

    // Then
    verify(variableScope, times(2)).put(anyString(), any(JsonNode.class));
  }
}
