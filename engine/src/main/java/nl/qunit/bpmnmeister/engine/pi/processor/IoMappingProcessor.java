package nl.qunit.bpmnmeister.engine.pi.processor;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.qunit.bpmnmeister.engine.pi.ScopedVars;
import nl.qunit.bpmnmeister.engine.pi.feel.FeelExpressionHandler;
import nl.qunit.bpmnmeister.pd.model.IoVariableMappingDTO;
import nl.qunit.bpmnmeister.pd.model.WithIoMapping;
import nl.qunit.bpmnmeister.pi.VariablesDTO;

@ApplicationScoped
public class IoMappingProcessor {

  @Inject FeelExpressionHandler feelExpressionHandler;

  public VariablesDTO getOutputVariables(WithIoMapping element, ScopedVars inputVariables) {
    VariablesDTO catchEventVariables = inputVariables.getCurrentScopeVariables();
    if (element.getIoMapping().getOutputMappings().isEmpty()) {
      // No mappings, return all input variables unmodified
      return catchEventVariables;
    }

    VariablesDTO outputVariables = VariablesDTO.empty();
    for (IoVariableMappingDTO mapping : element.getIoMapping().getOutputMappings()) {
      String varName = mapping.getTarget();
      JsonNode jsonNode =
          feelExpressionHandler.processFeelExpression(mapping.getSource(), inputVariables);
      outputVariables.put(varName, jsonNode);
    }
    return outputVariables;
  }

  public VariablesDTO getInputVariables(WithIoMapping element, ScopedVars variables) {
    VariablesDTO catchEventVariables = variables.getCurrentScopeVariables();
    if (element.getIoMapping().getInputMappings().isEmpty()) {
      // No mappings, return all input variables unmodified
      return catchEventVariables;
    }

    VariablesDTO inputVariables = VariablesDTO.empty();
    for (IoVariableMappingDTO mapping : element.getIoMapping().getInputMappings()) {
      String varName = mapping.getTarget();
      JsonNode jsonNode =
          feelExpressionHandler.processFeelExpression(mapping.getSource(), variables);
      inputVariables.put(varName, jsonNode);
    }
    return inputVariables;
  }
}
