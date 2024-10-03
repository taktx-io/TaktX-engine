package nl.qunit.bpmnmeister.engine.pi.processor;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.NoArgsConstructor;
import lombok.Setter;
import nl.qunit.bpmnmeister.pd.model.IoVariableMapping;
import nl.qunit.bpmnmeister.pd.model.WithIoMapping;
import nl.qunit.bpmnmeister.pi.FeelExpressionHandler;
import nl.qunit.bpmnmeister.pi.Variables;

@ApplicationScoped
@NoArgsConstructor
@Setter
public class IoMappingProcessor {

  private FeelExpressionHandler feelExpressionHandler;

  @Inject
  public IoMappingProcessor(FeelExpressionHandler feelExpressionHandler) {
    this.feelExpressionHandler = feelExpressionHandler;
  }

  public Variables getOutputVariables(WithIoMapping element, Variables inputVariables) {
    if (element.getIoMapping().getOutputMappings().isEmpty()) {
      // No mappings, return all input variables unmodified
      return inputVariables;
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

  public Variables getInputVariables(WithIoMapping element, Variables variables) {
    if (element.getIoMapping().getInputMappings().isEmpty()) {
      // No mappings, return all input variables unmodified
      return variables;
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
