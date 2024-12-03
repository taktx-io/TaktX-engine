package nl.qunit.bpmnmeister.pi;

import com.fasterxml.jackson.databind.JsonNode;

public interface FeelExpressionHandler {

  JsonNode processFeelExpression(String expression, Variables variables);
}
