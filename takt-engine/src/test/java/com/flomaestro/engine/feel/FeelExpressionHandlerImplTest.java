package com.flomaestro.engine.feel;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.TextNode;
import com.flomaestro.engine.pi.model.ProcessInstanceVariables;
import org.junit.jupiter.api.Test;

class FeelExpressionHandlerImplTest {

  @Test
  void testNoFeel() {
    FeelExpressionHandlerImpl expressionHandler =
        new FeelExpressionHandlerImpl(new FeelEngineProvider(), new ObjectMapper());
    JsonNode jsonNode =
        expressionHandler.processFeelExpression("test", ProcessInstanceVariables.empty());
    assertThat(jsonNode.asText()).isEqualTo("test");
  }

  @Test
  void testSimpleFeel() {
    FeelExpressionHandlerImpl expressionHandler =
        new FeelExpressionHandlerImpl(new FeelEngineProvider(), new ObjectMapper());
    JsonNode jsonNode =
        expressionHandler.processFeelExpression("=\"test\"", ProcessInstanceVariables.empty());
    assertThat(jsonNode.asText()).isEqualTo("test");
  }

  @Test
  void testReferToVariableFeel() {
    FeelExpressionHandlerImpl expressionHandler =
        new FeelExpressionHandlerImpl(new FeelEngineProvider(), new ObjectMapper());
    ProcessInstanceVariables vars = ProcessInstanceVariables.empty();
    vars.put("var", new TextNode("test"));
    JsonNode jsonNode = expressionHandler.processFeelExpression("=var", vars);
    assertThat(jsonNode.asText()).isEqualTo("test");
  }

  @Test
  void testReferToVariableNotExisting() {
    FeelExpressionHandlerImpl expressionHandler =
        new FeelExpressionHandlerImpl(new FeelEngineProvider(), new ObjectMapper());
    ProcessInstanceVariables vars = ProcessInstanceVariables.empty();
    vars.put("var", new TextNode("test"));
    JsonNode jsonNode = expressionHandler.processFeelExpression("=var2", vars);
    assertThat(jsonNode.asText()).isEqualTo("null");
  }

  @Test
  void testCreateRange() {
    FeelExpressionHandlerImpl expressionHandler =
        new FeelExpressionHandlerImpl(new FeelEngineProvider(), new ObjectMapper());
    ProcessInstanceVariables vars = ProcessInstanceVariables.empty();

    JsonNode jsonNode = expressionHandler.processFeelExpression("=for i in 1..100 return i", vars);
    assertThat(jsonNode.isArray()).isTrue();
    assertThat(jsonNode.size()).isEqualTo(100);
  }

  @Test
  void testReferToElementInArray() {
    FeelExpressionHandlerImpl expressionHandler =
        new FeelExpressionHandlerImpl(new FeelEngineProvider(), new ObjectMapper());
    ProcessInstanceVariables vars = ProcessInstanceVariables.empty();
    ArrayNode arrayNode = new ObjectMapper().createArrayNode();
    arrayNode.add("test1");
    arrayNode.add("test2");
    arrayNode.add("test3");
    vars.put("myArray", arrayNode);

    JsonNode jsonNode = expressionHandler.processFeelExpression("=myArray[2]", vars);
    assertThat(jsonNode.asText()).isEqualTo("test2");
  }
}
