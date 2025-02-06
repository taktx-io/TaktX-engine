package com.flomaestro.engine.pi.processor;

import com.fasterxml.jackson.databind.JsonNode;
import com.flomaestro.engine.feel.FeelExpressionHandler;
import com.flomaestro.engine.pd.model.IoVariableMapping;
import com.flomaestro.engine.pd.model.WithIoMapping;
import com.flomaestro.engine.pi.model.VariableScope;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Set;
import lombok.Setter;

@ApplicationScoped
@Setter
public class IoMappingProcessor {

  private FeelExpressionHandler feelExpressionHandler;

  @Inject
  public IoMappingProcessor(FeelExpressionHandler feelExpressionHandler) {
    this.feelExpressionHandler = feelExpressionHandler;
  }

  public void processOutputMappings(WithIoMapping element, VariableScope variables) {
    Set<IoVariableMapping> outputMappings = element.getIoMapping().getOutputMappings();

    addVariables(variables, outputMappings);
  }

  public void addVariables(VariableScope variables, Set<IoVariableMapping> outputMappings) {
    if (outputMappings.isEmpty()) {
      variables.mergeAllToParent();
    } else {
      for (IoVariableMapping mapping : outputMappings) {
        String varName = mapping.getTarget();
        JsonNode jsonNode =
            feelExpressionHandler.processFeelExpression(mapping.getSource(), variables);
        variables.putInParent(varName, jsonNode);
      }
    }
  }

  public void addInputVariables(WithIoMapping element, VariableScope variables) {
    Set<IoVariableMapping> inputMappings = element.getIoMapping().getInputMappings();
    if (inputMappings.isEmpty()) {
      return;
    }

    addVariables(variables, inputMappings);
  }
}
