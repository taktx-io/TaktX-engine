/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */

package io.taktx.engine.dmn;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.taktx.dto.DmnCollectOperator;
import io.taktx.dto.DmnDecisionDTO;
import io.taktx.dto.DmnDecisionTableDTO;
import io.taktx.dto.DmnHitPolicy;
import io.taktx.dto.DmnInputClauseDTO;
import io.taktx.dto.DmnLiteralExpressionDTO;
import io.taktx.dto.DmnOutputClauseDTO;
import io.taktx.dto.DmnRuleDTO;
import io.taktx.engine.pi.model.VariableScope;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.camunda.feel.FeelEngineClock.SystemClock$;
import org.camunda.feel.api.EvaluationResult;
import org.camunda.feel.api.FeelEngineApi;
import org.camunda.feel.api.ParseResult;
import org.camunda.feel.api.SuccessfulEvaluationResult;
import org.camunda.feel.context.Context;
import org.camunda.feel.context.FunctionProvider;
import org.camunda.feel.context.VariableProvider;
import org.camunda.feel.impl.interpreter.BuiltinFunctions;
import org.camunda.feel.syntaxtree.ParsedExpression;
import org.camunda.feel.valuemapper.ValueMapper;
import scala.Option;
import scala.collection.Iterable;
import scala.jdk.CollectionConverters;

@ApplicationScoped
@Slf4j
public class DmnEvaluatorImpl implements DmnEvaluator {

  private static final BuiltinFunctions BUILTIN_FUNCTIONS =
      new BuiltinFunctions(new SystemClock$(), ValueMapper.defaultValueMapper());

  private final FeelEngineApi feelEngineApi;
  private final ObjectMapper objectMapper;
  /** Thread-safe cache of parsed FEEL expressions for performance. */
  private final Map<String, ParsedExpression> expressionCache = new ConcurrentHashMap<>();

  public DmnEvaluatorImpl(FeelEngineApi feelEngineApi, ObjectMapper objectMapper) {
    this.feelEngineApi = feelEngineApi;
    this.objectMapper = objectMapper;
  }

  // ── public API ───────────────────────────────────────────────────────────

  @Override
  public JsonNode evaluate(DmnDecisionDTO decision, VariableScope variables) {
    if (decision.getDecisionTable() != null) {
      return evaluateDecisionTable(decision.getDecisionTable(), variables);
    } else if (decision.getLiteralExpression() != null) {
      return evaluateLiteralExpression(decision.getLiteralExpression(), variables);
    }
    throw new IllegalArgumentException(
        "Decision '" + decision.getId() + "' has neither a decisionTable nor a literalExpression");
  }

  // ── literal expression ───────────────────────────────────────────────────

  private JsonNode evaluateLiteralExpression(
      DmnLiteralExpressionDTO literalExpression, VariableScope variables) {
    String expr = literalExpression.getExpression();
    if (expr == null || expr.isBlank()) {
      return objectMapper.nullNode();
    }
    return evaluateFeelOutputExpression(expr, variables);
  }

  // ── decision table ────────────────────────────────────────────────────────

  private JsonNode evaluateDecisionTable(DmnDecisionTableDTO table, VariableScope variables) {
    DmnHitPolicy hitPolicy =
        table.getHitPolicy() != null ? table.getHitPolicy() : DmnHitPolicy.UNIQUE;

    List<ObjectNode> matchedRows = evaluateRules(table, variables);

    return switch (hitPolicy) {
      case UNIQUE -> handleUnique(matchedRows, table);
      case FIRST -> handleFirst(matchedRows, table);
      case ANY -> handleAny(matchedRows, table);
      case COLLECT -> handleCollect(matchedRows, table);
      case RULE_ORDER -> handleRuleOrder(matchedRows, table);
      case OUTPUT_ORDER -> handleOutputOrder(matchedRows, table);
      case PRIORITY -> handlePriority(matchedRows, table);
    };
  }

  /**
   * Evaluates all rules and returns the matched output rows as a list of ObjectNodes (each node
   * maps output-clause name → evaluated output value).
   */
  private List<ObjectNode> evaluateRules(DmnDecisionTableDTO table, VariableScope variables) {
    List<ObjectNode> matched = new ArrayList<>();
    for (DmnRuleDTO rule : table.getRules()) {
      if (inputsMatch(table.getInputs(), rule, variables)) {
        matched.add(buildOutputNode(table.getOutputs(), rule, variables));
      }
    }
    return matched;
  }

  private boolean inputsMatch(
      List<DmnInputClauseDTO> inputs, DmnRuleDTO rule, VariableScope variables) {
    List<String> inputEntries = rule.getInputEntries();
    for (int i = 0; i < inputs.size(); i++) {
      String entry = i < inputEntries.size() ? inputEntries.get(i) : "";
      if (entry == null || entry.isBlank()) {
        // Wildcard – always matches
        continue;
      }
      DmnInputClauseDTO clause = inputs.get(i);
      JsonNode inputValue = evaluateFeelOutputExpression(clause.getInputExpression(), variables);
      if (!evaluateUnaryTest(entry, inputValue, variables)) {
        return false;
      }
    }
    return true;
  }

  private ObjectNode buildOutputNode(
      List<DmnOutputClauseDTO> outputs, DmnRuleDTO rule, VariableScope variables) {
    ObjectNode result = objectMapper.createObjectNode();
    List<String> outputEntries = rule.getOutputEntries();
    for (int i = 0; i < outputs.size(); i++) {
      DmnOutputClauseDTO output = outputs.get(i);
      String entry = i < outputEntries.size() ? outputEntries.get(i) : "";
      JsonNode value =
          (entry == null || entry.isBlank())
              ? objectMapper.nullNode()
              : evaluateFeelOutputExpression(entry, variables);
      result.set(output.getName(), value == null ? objectMapper.nullNode() : value);
    }
    return result;
  }

  // ── hit-policy handlers ───────────────────────────────────────────────────

  private JsonNode handleUnique(List<ObjectNode> matched, DmnDecisionTableDTO table) {
    if (matched.isEmpty()) {
      return objectMapper.nullNode();
    }
    if (matched.size() > 1) {
      log.warn(
          "UNIQUE hit policy violated for decision table '{}': {} rules matched",
          table.getId(),
          matched.size());
    }
    return flattenSingleOutput(matched.get(0), table);
  }

  private JsonNode handleFirst(List<ObjectNode> matched, DmnDecisionTableDTO table) {
    if (matched.isEmpty()) {
      return objectMapper.nullNode();
    }
    return flattenSingleOutput(matched.get(0), table);
  }

  private JsonNode handleAny(List<ObjectNode> matched, DmnDecisionTableDTO table) {
    if (matched.isEmpty()) {
      return objectMapper.nullNode();
    }
    return flattenSingleOutput(matched.get(0), table);
  }

  private JsonNode handleRuleOrder(List<ObjectNode> matched, DmnDecisionTableDTO table) {
    if (matched.isEmpty()) {
      return objectMapper.nullNode();
    }
    if (table.getOutputs().size() == 1) {
      ArrayNode arr = objectMapper.createArrayNode();
      for (ObjectNode row : matched) {
        arr.add(row.get(table.getOutputs().get(0).getName()));
      }
      return arr;
    }
    ArrayNode arr = objectMapper.createArrayNode();
    matched.forEach(arr::add);
    return arr;
  }

  private JsonNode handleOutputOrder(List<ObjectNode> matched, DmnDecisionTableDTO table) {
    // Output order has the same structure as rule order (ordering is defined at authoring time)
    return handleRuleOrder(matched, table);
  }

  private JsonNode handlePriority(List<ObjectNode> matched, DmnDecisionTableDTO table) {
    // Priority returns the first matched row (rules are already ordered by priority in the DMN)
    return handleFirst(matched, table);
  }

  private JsonNode handleCollect(List<ObjectNode> matched, DmnDecisionTableDTO table) {
    DmnCollectOperator op =
        table.getCollectOperator() != null ? table.getCollectOperator() : DmnCollectOperator.NONE;

    if (matched.isEmpty()) {
      return objectMapper.nullNode();
    }

    if (op == DmnCollectOperator.NONE) {
      return handleRuleOrder(matched, table);
    }

    // Aggregate operators work on the first output column
    String outputName =
        table.getOutputs().isEmpty() ? null : table.getOutputs().get(0).getName();

    List<Double> values = new ArrayList<>();
    for (ObjectNode row : matched) {
      JsonNode val = outputName != null ? row.get(outputName) : null;
      if (val != null && val.isNumber()) {
        values.add(val.asDouble());
      }
    }

    return switch (op) {
      case SUM -> objectMapper.valueToTree(values.stream().mapToDouble(d -> d).sum());
      case MIN -> {
        var min = values.stream().mapToDouble(d -> d).min();
        yield min.isPresent() ? objectMapper.valueToTree(min.getAsDouble()) : objectMapper.nullNode();
      }
      case MAX -> {
        var max = values.stream().mapToDouble(d -> d).max();
        yield max.isPresent() ? objectMapper.valueToTree(max.getAsDouble()) : objectMapper.nullNode();
      }
      case COUNT -> objectMapper.valueToTree((double) matched.size());
      default -> handleRuleOrder(matched, table);
    };
  }

  // ── FEEL helpers ──────────────────────────────────────────────────────────

  /** Evaluates a FEEL output expression (e.g. {@code "Roastbeef"}, {@code =someVar}). */
  private JsonNode evaluateFeelOutputExpression(String expression, VariableScope variables) {
    if (expression == null || expression.isBlank()) {
      return objectMapper.nullNode();
    }
    // Prefix with '=' to use the full FEEL expression evaluator (same as FeelExpressionHandlerImpl)
    String feelExpr = expression.startsWith("=") ? expression : "=" + expression;
    ParsedExpression parsed = getOrParseExpression(feelExpr);
    if (parsed == null) {
      log.warn("Failed to parse FEEL expression: {}", expression);
      return objectMapper.nullNode();
    }
    EvaluationResult result = feelEngineApi.evaluate(parsed, buildContext(variables));
    if (result.isSuccess()) {
      Object val = ((SuccessfulEvaluationResult) result).productIterator().next();
      return objectMapper.valueToTree(val);
    }
    log.warn("FEEL expression evaluation failed for '{}': {}", expression, result);
    return objectMapper.nullNode();
  }

  /**
   * Evaluates a FEEL unary test (e.g. {@code "Fall"}, {@code > 10}, {@code [1..5]}) against a
   * given input value using the FEEL {@code evaluateWithInput} API.
   */
  private boolean evaluateUnaryTest(
      String unaryTest, JsonNode inputValue, VariableScope variables) {
    ParsedExpression parsed = getOrParseUnaryTest(unaryTest.trim());
    if (parsed == null) {
      log.warn("Failed to parse FEEL unary test: {}", unaryTest);
      return false;
    }
    try {
      Object inputObj = objectMapper.treeToValue(inputValue, Object.class);
      EvaluationResult result =
          feelEngineApi.evaluateWithInput(parsed, inputObj, buildContext(variables));
      if (result.isSuccess()) {
        Object val = ((SuccessfulEvaluationResult) result).result();
        JsonNode node = objectMapper.valueToTree(val);
        return node.isBoolean() && node.booleanValue();
      }
      log.warn("FEEL unary test evaluation failed for '{}': {}", unaryTest, result);
      return false;
    } catch (JsonProcessingException e) {
      throw new IllegalStateException("Failed to convert input value for FEEL unary test", e);
    }
  }

  private ParsedExpression getOrParseExpression(String feelExpr) {
    ParsedExpression cached = expressionCache.get(feelExpr);
    if (cached != null) {
      return cached;
    }
    ParseResult pr = feelEngineApi.parseExpression(feelExpr.substring(1)); // strip '='
    if (pr.isSuccess()) {
      expressionCache.put(feelExpr, pr.parsedExpression());
      return pr.parsedExpression();
    }
    return null;
  }

  private ParsedExpression getOrParseUnaryTest(String unaryTest) {
    String cacheKey = "UT:" + unaryTest;
    ParsedExpression cached = expressionCache.get(cacheKey);
    if (cached != null) {
      return cached;
    }
    ParseResult pr = feelEngineApi.parseUnaryTests(unaryTest);
    if (pr.isSuccess()) {
      expressionCache.put(cacheKey, pr.parsedExpression());
      return pr.parsedExpression();
    }
    return null;
  }

  private Context buildContext(VariableScope variables) {
    return new Context() {
      @Override
      public VariableProvider variableProvider() {
        return new VariableProvider() {
          @Override
          public Option<Object> getVariable(String name) {
            try {
              JsonNode node = variables.get(name);
              if (node == null) {
                return Option.empty();
              }
              return Option.apply(objectMapper.treeToValue(node, Object.class));
            } catch (JsonProcessingException e) {
              throw new IllegalStateException(e);
            }
          }

          @Override
          public Iterable<String> keys() {
            return CollectionConverters.SetHasAsScala(variables.getVariables().keySet()).asScala();
          }
        };
      }

      @Override
      public FunctionProvider functionProvider() {
        return BUILTIN_FUNCTIONS;
      }
    };
  }

  /**
   * For single-output tables, returns the value directly. For multi-output tables, returns the
   * full output object.
   */
  private JsonNode flattenSingleOutput(ObjectNode row, DmnDecisionTableDTO table) {
    if (table.getOutputs() != null && table.getOutputs().size() == 1) {
      return row.get(table.getOutputs().get(0).getName());
    }
    return row;
  }
}
