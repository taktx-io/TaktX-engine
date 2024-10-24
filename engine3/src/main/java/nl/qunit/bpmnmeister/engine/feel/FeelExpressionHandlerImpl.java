package nl.qunit.bpmnmeister.engine.feel;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import jakarta.enterprise.context.ApplicationScoped;
import nl.qunit.bpmnmeister.pi.FeelExpressionHandler;
import nl.qunit.bpmnmeister.pi.Variables;
import org.camunda.feel.FeelEngine;
import org.camunda.feel.api.EvaluationResult;
import org.camunda.feel.api.FeelEngineApi;
import org.camunda.feel.api.SuccessfulEvaluationResult;
import scala.collection.Iterable;
import scala.jdk.CollectionConverters;

@ApplicationScoped
public class FeelExpressionHandlerImpl implements FeelExpressionHandler {
  private final FeelEngineProvider feelEngineProvider;
  private final ObjectMapper objectMapper;

  public FeelExpressionHandlerImpl(FeelEngineProvider feelEngineProvider,
      ObjectMapper objectMapper) {
    this.feelEngineProvider = feelEngineProvider;
    this.objectMapper = objectMapper;
  }

  public JsonNode processFeelExpression(String expression, Variables variables) {
    JsonNode resultNode;
    expression = expression == null ? "" : expression.trim();
    if (expression.startsWith("=")) {
      FeelEngine engine = feelEngineProvider.getFeelEngine();
      FeelEngineApi feelEngineApi = new FeelEngineApi(engine);

      EvaluationResult evaluationResult =
          feelEngineApi.evaluateExpression(expression.substring(1), WrappedInMap.of(variables, objectMapper));
      if (evaluationResult.isSuccess()) {
        Object expressionResult =
            ((SuccessfulEvaluationResult) evaluationResult).productIterator().next();
        Object rawResult;
        if (expressionResult instanceof Iterable<?> iterable) {
          rawResult = CollectionConverters.IterableHasAsJava(iterable).asJavaCollection();
        } else {
          rawResult = expressionResult;
        }
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
