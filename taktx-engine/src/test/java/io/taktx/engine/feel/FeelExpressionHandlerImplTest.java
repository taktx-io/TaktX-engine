/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.feel;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.TextNode;
import io.taktx.engine.pi.model.Scope;
import io.taktx.engine.pi.model.VariableScope;
import org.junit.jupiter.api.Test;

class FeelExpressionHandlerImplTest {

  @Test
  void testNoFeel() {
    FeelExpressionHandlerImpl expressionHandler =
        new FeelExpressionHandlerImpl(new FeelEngineProvider(), new ObjectMapper());
    Scope scope = new Scope();
    JsonNode jsonNode = expressionHandler.processFeelExpression("test", VariableScope.empty(scope));
    assertThat(jsonNode.asText()).isEqualTo("test");
  }

  @Test
  void testSimpleFeel() {
    FeelExpressionHandlerImpl expressionHandler =
        new FeelExpressionHandlerImpl(new FeelEngineProvider(), new ObjectMapper());
    Scope scope = new Scope();
    JsonNode jsonNode =
        expressionHandler.processFeelExpression("=\"test\"", VariableScope.empty(scope));
    assertThat(jsonNode.asText()).isEqualTo("test");
  }

  @Test
  void testReferToVariableFeel() {
    FeelExpressionHandlerImpl expressionHandler =
        new FeelExpressionHandlerImpl(new FeelEngineProvider(), new ObjectMapper());
    Scope scope = new Scope();
    VariableScope vars = VariableScope.empty(scope);
    vars.put("var", new TextNode("test"));
    JsonNode jsonNode = expressionHandler.processFeelExpression("=var", vars);
    assertThat(jsonNode.asText()).isEqualTo("test");
  }

  @Test
  void testReferToVariableNotExisting() {
    FeelExpressionHandlerImpl expressionHandler =
        new FeelExpressionHandlerImpl(new FeelEngineProvider(), new ObjectMapper());
    Scope scope = new Scope();
    VariableScope vars = VariableScope.empty(scope);
    vars.put("var", new TextNode("test"));
    JsonNode jsonNode = expressionHandler.processFeelExpression("=var2", vars);
    assertThat(jsonNode.asText()).isEqualTo("null");
  }

  @Test
  void testCreateRange() {
    FeelExpressionHandlerImpl expressionHandler =
        new FeelExpressionHandlerImpl(new FeelEngineProvider(), new ObjectMapper());
    Scope scope = new Scope();
    VariableScope vars = VariableScope.empty(scope);

    JsonNode jsonNode = expressionHandler.processFeelExpression("=for i in 1..100 return i", vars);
    assertThat(jsonNode.isArray()).isTrue();
    assertThat(jsonNode.size()).isEqualTo(100);
  }

  @Test
  void testReferToElementInArray() {
    FeelExpressionHandlerImpl expressionHandler =
        new FeelExpressionHandlerImpl(new FeelEngineProvider(), new ObjectMapper());
    Scope scope = new Scope();
    VariableScope vars = VariableScope.empty(scope);
    ArrayNode arrayNode = new ObjectMapper().createArrayNode();
    arrayNode.add("test1");
    arrayNode.add("test2");
    arrayNode.add("test3");
    vars.put("myArray", arrayNode);

    JsonNode jsonNode = expressionHandler.processFeelExpression("=myArray[2]", vars);
    assertThat(jsonNode.asText()).isEqualTo("test2");
  }
}
