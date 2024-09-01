package nl.qunit.bpmnmeister.engine.pi.processor;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.NoArgsConstructor;
import lombok.Setter;
import nl.qunit.bpmnmeister.pd.model.IoVariableMapping2;
import nl.qunit.bpmnmeister.pd.model.WithIoMapping;
import nl.qunit.bpmnmeister.pi.FeelExpressionHandler;
import nl.qunit.bpmnmeister.pi.Variables2;

@ApplicationScoped
@NoArgsConstructor
@Setter
public class IoMappingProcessor {

  private FeelExpressionHandler feelExpressionHandler;

  @Inject
  public IoMappingProcessor(FeelExpressionHandler feelExpressionHandler) {
    this.feelExpressionHandler = feelExpressionHandler;
  }

  public Variables2 getOutputVariables(WithIoMapping element, Variables2 inputVariables) {
    if (element.getIoMapping().getOutputMappings().isEmpty()) {
      // No mappings, return all input variables unmodified
      return inputVariables;
    }

    Variables2 outputVariables = Variables2.empty();
    for (IoVariableMapping2 mapping : element.getIoMapping().getOutputMappings()) {
      String varName = mapping.getTarget();
      JsonNode jsonNode =
          feelExpressionHandler.processFeelExpression(mapping.getSource(), inputVariables);
      outputVariables.put(varName, jsonNode);
    }
    return outputVariables;
  }

  public Variables2 getInputVariables(WithIoMapping element, Variables2 variables) {
    if (element.getIoMapping().getInputMappings().isEmpty()) {
      // No mappings, return all input variables unmodified
      return variables;
    }

    Variables2 inputVariables = Variables2.empty();
    for (IoVariableMapping2 mapping : element.getIoMapping().getInputMappings()) {
      String varName = mapping.getTarget();
      JsonNode jsonNode =
          feelExpressionHandler.processFeelExpression(mapping.getSource(), variables);
      inputVariables.put(varName, jsonNode);
    }
    return inputVariables;
  }
}
