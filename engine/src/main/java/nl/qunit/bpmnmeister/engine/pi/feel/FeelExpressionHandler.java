package nl.qunit.bpmnmeister.engine.pi.feel;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import nl.qunit.bpmnmeister.pi.Variables;
import org.camunda.feel.FeelEngine;
import org.camunda.feel.api.EvaluationResult;
import org.camunda.feel.api.FeelEngineApi;

@ApplicationScoped
@RequiredArgsConstructor
public class FeelExpressionHandler {
  private final FeelEngineProvider feelEngineProvider;

  public EvaluationResult processFeelExpression(String expression, Variables variables) {
    FeelEngine engine = feelEngineProvider.getFeelEngine();
    FeelEngineApi feelEngineApi = new FeelEngineApi(engine);
    Map<String, Object> objectMap =
        variables.getVariables().entrySet().stream()
            .collect(
                Collectors.toMap(
                    Map.Entry::getKey,
                    e -> {
                      try {
                        return new ObjectMapper().treeToValue(e.getValue(), Object.class);
                      } catch (JsonProcessingException ex) {
                        throw new RuntimeException(ex);
                      }
                    }));
    return feelEngineApi.evaluateExpression(expression, objectMap);
  }
}
