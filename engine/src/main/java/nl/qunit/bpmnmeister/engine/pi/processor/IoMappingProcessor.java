package nl.qunit.bpmnmeister.engine.pi.processor;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import nl.qunit.bpmnmeister.engine.pi.feel.FeelExpressionHandler;
import nl.qunit.bpmnmeister.pd.model.IoVariableMapping;
import nl.qunit.bpmnmeister.pd.model.WithIoMapping;
import nl.qunit.bpmnmeister.pi.Variables;

@ApplicationScoped
public class IoMappingProcessor {

  @Inject FeelExpressionHandler feelExpressionHandler;

  public Variables getOutputVariables(WithIoMapping element, Variables inputVariables) {
    Variables catchEventVariables = inputVariables;
    for (IoVariableMapping mapping : element.getIoMapping().getOutputMappings()) {
      String varName = mapping.getTarget();
      JsonNode jsonNode =
          feelExpressionHandler.processFeelExpression(mapping.getSource(), inputVariables);
      catchEventVariables = catchEventVariables.put(varName, jsonNode);
    }
    return catchEventVariables;
  }

  public Variables getInputVariables(WithIoMapping element, Variables variables) {
    Variables catchEventVariables = Variables.EMPTY;
    for (IoVariableMapping mapping : element.getIoMapping().getInputMappings()) {
      String varName = mapping.getTarget();
      JsonNode jsonNode =
          feelExpressionHandler.processFeelExpression(mapping.getSource(), variables);
      catchEventVariables = catchEventVariables.put(varName, jsonNode);
    }
    return catchEventVariables;
  }
}
