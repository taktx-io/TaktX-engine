package nl.qunit.bpmnmeister.engine.feel;

import com.fasterxml.jackson.databind.JsonNode;
import nl.qunit.bpmnmeister.engine.pi.model.Variables;

public interface FeelExpressionHandler {

  JsonNode processFeelExpression(String expression, Variables variables);
}
