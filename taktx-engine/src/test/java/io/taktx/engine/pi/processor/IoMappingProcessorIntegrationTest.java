/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.pi.processor;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.TextNode;
import io.taktx.engine.feel.FeelExpressionHandler;
import io.taktx.engine.pd.model.IoVariableMapping;
import io.taktx.engine.pi.model.Scope;
import io.taktx.engine.pi.model.VariableScope;
import java.util.HashMap;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

/** Integration test demonstrating the nested variable path feature with realistic scenarios. */
class IoMappingProcessorIntegrationTest {

  private IoMappingProcessor processor;
  private ObjectMapper objectMapper;

  @Mock private FeelExpressionHandler feelExpressionHandler;

  @Mock private Scope scope;

  @BeforeEach
  void setUp() {
    MockitoAnnotations.openMocks(this);
    objectMapper = new ObjectMapper();
    processor = new IoMappingProcessor(feelExpressionHandler, objectMapper);
  }

  @Test
  void testComplexNestedVariableScenario() {
    // Given: A real VariableScope implementation
    VariableScope variableScope = VariableScope.empty(scope);

    // Scenario: We're mapping customer data from a service response
    IoVariableMapping nameMapping = new IoVariableMapping("response.name", "customer.name");
    IoVariableMapping emailMapping =
        new IoVariableMapping("response.email", "customer.contact.email");
    IoVariableMapping phoneMapping =
        new IoVariableMapping("response.phone", "customer.contact.phone");
    IoVariableMapping ageMapping = new IoVariableMapping("response.age", "customer.age");
    IoVariableMapping cityMapping = new IoVariableMapping("response.city", "customer.address.city");
    IoVariableMapping countryMapping =
        new IoVariableMapping("response.country", "customer.address.country");

    // Mock the FEEL expression handler to return values
    org.mockito.Mockito.when(
            feelExpressionHandler.processFeelExpression("response.name", variableScope))
        .thenReturn(new TextNode("John Doe"));
    org.mockito.Mockito.when(
            feelExpressionHandler.processFeelExpression("response.email", variableScope))
        .thenReturn(new TextNode("john@example.com"));
    org.mockito.Mockito.when(
            feelExpressionHandler.processFeelExpression("response.phone", variableScope))
        .thenReturn(new TextNode("+1234567890"));
    org.mockito.Mockito.when(
            feelExpressionHandler.processFeelExpression("response.age", variableScope))
        .thenReturn(new IntNode(35));
    org.mockito.Mockito.when(
            feelExpressionHandler.processFeelExpression("response.city", variableScope))
        .thenReturn(new TextNode("New York"));
    org.mockito.Mockito.when(
            feelExpressionHandler.processFeelExpression("response.country", variableScope))
        .thenReturn(new TextNode("USA"));

    // When: Process all mappings
    processor.addVariables(
        variableScope,
        Set.of(nameMapping, emailMapping, phoneMapping, ageMapping, cityMapping, countryMapping));

    // Then: Verify the nested structure is correctly created
    HashMap<String, JsonNode> variables = variableScope.getVariables();
    assertTrue(variables.containsKey("customer"));

    JsonNode customer = variables.get("customer");
    assertTrue(customer.isObject());

    // Check top-level properties
    assertEquals("John Doe", customer.get("name").asText());
    assertEquals(35, customer.get("age").asInt());

    // Check nested contact object
    assertTrue(customer.has("contact"));
    JsonNode contact = customer.get("contact");
    assertTrue(contact.isObject());
    assertEquals("john@example.com", contact.get("email").asText());
    assertEquals("+1234567890", contact.get("phone").asText());

    // Check nested address object
    assertTrue(customer.has("address"));
    JsonNode address = customer.get("address");
    assertTrue(address.isObject());
    assertEquals("New York", address.get("city").asText());
    assertEquals("USA", address.get("country").asText());
  }

  @Test
  void testMergingWithExistingData() {
    // Given: VariableScope with existing customer data
    VariableScope variableScope = VariableScope.empty(scope);

    // Pre-populate with some customer data
    com.fasterxml.jackson.databind.node.ObjectNode existingCustomer =
        objectMapper.createObjectNode();
    existingCustomer.put("id", "12345");
    existingCustomer.put("status", "active");
    variableScope.put("customer", existingCustomer);

    // New mappings to add more fields
    IoVariableMapping nameMapping = new IoVariableMapping("input.name", "customer.name");
    IoVariableMapping emailMapping = new IoVariableMapping("input.email", "customer.email");

    org.mockito.Mockito.when(
            feelExpressionHandler.processFeelExpression("input.name", variableScope))
        .thenReturn(new TextNode("Jane Smith"));
    org.mockito.Mockito.when(
            feelExpressionHandler.processFeelExpression("input.email", variableScope))
        .thenReturn(new TextNode("jane@example.com"));

    // When: Add new variables
    processor.addVariables(variableScope, Set.of(nameMapping, emailMapping));

    // Then: Existing data should be preserved
    JsonNode customer = variableScope.getVariables().get("customer");
    assertEquals("12345", customer.get("id").asText());
    assertEquals("active", customer.get("status").asText());
    assertEquals("Jane Smith", customer.get("name").asText());
    assertEquals("jane@example.com", customer.get("email").asText());
  }

  @Test
  void testDeeplyNestedPaths() {
    // Given: Very deep nested paths
    VariableScope variableScope = VariableScope.empty(scope);

    IoVariableMapping deepMapping =
        new IoVariableMapping("source", "level1.level2.level3.level4.value");

    org.mockito.Mockito.when(feelExpressionHandler.processFeelExpression("source", variableScope))
        .thenReturn(new TextNode("deep value"));

    // When
    processor.addVariables(variableScope, Set.of(deepMapping));

    // Then: Navigate through all levels
    JsonNode result = variableScope.getVariables().get("level1");
    assertTrue(result.isObject());

    result = result.get("level2");
    assertTrue(result.isObject());

    result = result.get("level3");
    assertTrue(result.isObject());

    result = result.get("level4");
    assertTrue(result.isObject());

    assertEquals("deep value", result.get("value").asText());
  }
}
