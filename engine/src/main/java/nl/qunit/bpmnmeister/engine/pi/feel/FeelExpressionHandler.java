package nl.qunit.bpmnmeister.engine.pi.feel;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import nl.qunit.bpmnmeister.pi.Variables;
import org.camunda.feel.FeelEngine;
import org.camunda.feel.api.EvaluationResult;
import org.camunda.feel.api.FeelEngineApi;
import org.camunda.feel.api.SuccessfulEvaluationResult;
import scala.collection.Iterable;
import scala.jdk.CollectionConverters;

@ApplicationScoped
@RequiredArgsConstructor
public class FeelExpressionHandler {
  private final FeelEngineProvider feelEngineProvider;

  public JsonNode processFeelExpression(String expression, Variables variables) {
    JsonNode resultNode;
    expression = expression.trim();
    if (expression.startsWith("=")) {
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
                          throw new IllegalStateException(ex);
                        }
                      }));
      EvaluationResult evaluationResult =
          feelEngineApi.evaluateExpression(expression.substring(1), objectMap);
      if (evaluationResult.isSuccess()) {
        Object expressionResult =
            ((SuccessfulEvaluationResult) evaluationResult).productIterator().next();
        Object rawResult;
        if (expressionResult instanceof Iterable<?> iterable) {
          rawResult = CollectionConverters.IterableHasAsJava(iterable).asJavaCollection();
        } else {
          rawResult = expressionResult;
        }
        ObjectMapper objectMapper = new ObjectMapper();
        resultNode = objectMapper.valueToTree(rawResult);
      } else {
        resultNode = null;
      }
    } else {
      resultNode = variables.get(expression);
      if (resultNode == null) {
        resultNode = new TextNode(expression);
      }
    }

    return resultNode;
  }
}
