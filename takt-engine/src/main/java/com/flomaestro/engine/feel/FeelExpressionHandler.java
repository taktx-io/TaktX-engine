package com.flomaestro.engine.feel;

import com.fasterxml.jackson.databind.JsonNode;
import com.flomaestro.engine.pi.model.AbstractVariableScope;

public interface FeelExpressionHandler {

  JsonNode processFeelExpression(String expression, AbstractVariableScope variables);
}
