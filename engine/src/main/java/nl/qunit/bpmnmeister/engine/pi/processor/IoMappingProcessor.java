package nl.qunit.bpmnmeister.engine.pi.processor;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.qunit.bpmnmeister.engine.pi.ScopedVars;
import nl.qunit.bpmnmeister.engine.pi.feel.FeelExpressionHandler;
import nl.qunit.bpmnmeister.pd.model.IoVariableMapping;
import nl.qunit.bpmnmeister.pd.model.WithIoMapping;
import nl.qunit.bpmnmeister.pi.Variables;

@ApplicationScoped
public class IoMappingProcessor {

  @Inject FeelExpressionHandler feelExpressionHandler;

  public Variables getOutputVariables(WithIoMapping element, ScopedVars inputVariables) {
    Variables catchEventVariables = inputVariables.getCurrentScopeVariables();
    if (element.getIoMapping().getOutputMappings().isEmpty()) {
      // No mappings, return all input variables unmodified
      return catchEventVariables;
    }

    Variables outputVariables = Variables.empty();
    for (IoVariableMapping mapping : element.getIoMapping().getOutputMappings()) {
      String varName = mapping.getTarget();
      JsonNode jsonNode =
          feelExpressionHandler.processFeelExpression(mapping.getSource(), inputVariables);
      outputVariables.put(varName, jsonNode);
    }
    return outputVariables;
  }

  public Variables getInputVariables(WithIoMapping element, ScopedVars variables) {
    Variables catchEventVariables = variables.getCurrentScopeVariables();
    if (element.getIoMapping().getInputMappings().isEmpty()) {
      // No mappings, return all input variables unmodified
      return catchEventVariables;
    }

    Variables inputVariables = Variables.empty();
    for (IoVariableMapping mapping : element.getIoMapping().getInputMappings()) {
      String varName = mapping.getTarget();
      JsonNode jsonNode =
          feelExpressionHandler.processFeelExpression(mapping.getSource(), variables);
      inputVariables.put(varName, jsonNode);
    }
    return inputVariables;
  }
}
