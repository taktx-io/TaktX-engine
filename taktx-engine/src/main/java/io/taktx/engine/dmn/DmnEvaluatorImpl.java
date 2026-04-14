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
import io.taktx.dto.DmnValidationMode;
import io.taktx.engine.config.GlobalConfigStore;
import io.taktx.engine.config.TaktConfiguration;
import io.taktx.engine.pi.model.VariableScope;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
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
      new BuiltinFunctions(SystemClock$.MODULE$, ValueMapper.defaultValueMapper());
  private static final Pattern SIMPLE_REFERENCE_PATTERN =
      Pattern.compile(
          "^([A-Za-z_][A-Za-z0-9_]*)(?:\\[(\\d+)\\])?(?:\\.([A-Za-z_][A-Za-z0-9_]*))?$");

  private final FeelEngineApi feelEngineApi;
  private final ObjectMapper objectMapper;

  /**
   * Optional: resolves required decisions from the deployed store to support DRG chaining. {@code
   * null} in unit-test contexts where no Kafka store is available.
   */
  private final DmnDecisionResolver decisionResolver;

  private final GlobalConfigStore globalConfigStore;
  private final DmnValidationMode configuredValidationMode;

  /** Thread-safe cache of parsed FEEL expressions for performance. */
  private final Map<String, ParsedExpression> expressionCache = new ConcurrentHashMap<>();

  /** CDI constructor used in production. */
  @Inject
  public DmnEvaluatorImpl(
      FeelEngineApi feelEngineApi,
      ObjectMapper objectMapper,
      DmnDecisionResolver decisionResolver,
      GlobalConfigStore globalConfigStore,
      TaktConfiguration taktConfiguration) {
    this(
        feelEngineApi,
        objectMapper,
        decisionResolver,
        globalConfigStore,
        taktConfiguration != null
            ? taktConfiguration.getDmnValidationMode()
            : DmnValidationMode.PERMISSIVE);
  }

  private DmnEvaluatorImpl(
      FeelEngineApi feelEngineApi,
      ObjectMapper objectMapper,
      DmnDecisionResolver decisionResolver,
      GlobalConfigStore globalConfigStore,
      DmnValidationMode configuredValidationMode) {
    this.feelEngineApi = feelEngineApi;
    this.objectMapper = objectMapper;
    this.decisionResolver = decisionResolver;
    this.globalConfigStore = globalConfigStore;
    this.configuredValidationMode =
        configuredValidationMode != null ? configuredValidationMode : DmnValidationMode.PERMISSIVE;
  }

  /** Convenience constructor for unit tests — DRG chaining is disabled (resolver is null). */
  public DmnEvaluatorImpl(FeelEngineApi feelEngineApi, ObjectMapper objectMapper) {
    this(feelEngineApi, objectMapper, null, null, DmnValidationMode.PERMISSIVE);
  }

  /** Convenience constructor for unit tests with DRG chaining support. */
  public DmnEvaluatorImpl(
      FeelEngineApi feelEngineApi,
      ObjectMapper objectMapper,
      DmnDecisionResolver decisionResolver) {
    this(feelEngineApi, objectMapper, decisionResolver, null, DmnValidationMode.PERMISSIVE);
  }

  /** Convenience constructor for unit tests with an explicit validation mode. */
  public DmnEvaluatorImpl(
      FeelEngineApi feelEngineApi,
      ObjectMapper objectMapper,
      DmnDecisionResolver decisionResolver,
      DmnValidationMode validationMode) {
    this(feelEngineApi, objectMapper, decisionResolver, null, validationMode);
  }

  // ── public API ───────────────────────────────────────────────────────────

  @Override
  public JsonNode evaluate(DmnDecisionDTO decision, VariableScope variables) {
    return evaluateDecision(decision, variables, new HashMap<>(), new LinkedHashSet<>());
  }

  // ── DRG-aware recursive evaluation ───────────────────────────────────────

  /**
   * Evaluates {@code decision} depth-first: required decisions are evaluated before the root, their
   * results are memoised in {@code drgResults}, and made visible via a lightweight overlay scope.
   *
   * @param inProgress decision IDs currently on the call stack — used for cycle detection
   */
  private JsonNode evaluateDecision(
      DmnDecisionDTO decision,
      VariableScope variables,
      Map<String, JsonNode> drgResults,
      LinkedHashSet<String> inProgress) {

    String decisionId = decision.getId();

    if (inProgress.contains(decisionId)) {
      throw new IllegalStateException(
          "Circular dependency detected in DMN decision graph: "
              + inProgress
              + " -> "
              + decisionId);
    }

    List<String> requiredIds = decision.getRequiredDecisionIds();
    if (requiredIds != null && !requiredIds.isEmpty() && decisionResolver != null) {
      inProgress.add(decisionId);
      try {
        for (String reqId : requiredIds) {
          if (!drgResults.containsKey(reqId)) {
            DmnDecisionDTO reqDecision =
                decisionResolver
                    .resolve(reqId)
                    .orElseThrow(
                        () ->
                            new IllegalStateException(
                                "Required decision '" + reqId + "' not found in any deployed DMN"));
            drgResults.put(reqId, evaluateDecision(reqDecision, variables, drgResults, inProgress));
          }
        }
      } finally {
        inProgress.remove(decisionId);
      }
    }

    // Build an overlay so required-decision results are readable as variables
    // without polluting the process-instance scope.
    VariableScope evalScope =
        drgResults.isEmpty() ? variables : createDrgOverlayScope(variables, drgResults);

    if (decision.getDecisionTable() != null) {
      return evaluateDecisionTable(decision.getDecisionTable(), evalScope);
    } else if (decision.getLiteralExpression() != null) {
      return evaluateLiteralExpression(decision.getLiteralExpression(), evalScope);
    }
    throw new IllegalArgumentException(
        "Decision '" + decisionId + "' has neither a decisionTable nor a literalExpression");
  }

  /**
   * Creates a thin read-only overlay scope whose in-memory map contains the DRG intermediate
   * results. Any variable not found there is delegated to the underlying process {@code base} scope
   * via the parent-scope chain that {@link VariableScope#get} already follows.
   */
  private VariableScope createDrgOverlayScope(
      VariableScope base, Map<String, JsonNode> drgResults) {
    VariableScope overlay = new VariableScope(base, null, null, null);
    drgResults.forEach((k, v) -> overlay.getVariables().put(k, v));
    return overlay;
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

    // Derive how many matched rows we actually need before we can stop evaluating.
    // FIRST / ANY / PRIORITY only need the first match.
    // UNIQUE needs at most 2 so we can still detect (and warn about) a violation.
    // Everything else must collect all matches.
    int maxMatches =
        switch (hitPolicy) {
          case FIRST, ANY, PRIORITY -> 1;
          case UNIQUE -> 2;
          default -> Integer.MAX_VALUE;
        };

    List<ObjectNode> matchedRows = evaluateRules(table, variables, maxMatches);

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
   * Evaluates rules in order and returns matched output rows. Stops as soon as {@code maxMatches}
   * rows have been collected, enabling early exit for hit policies that don't need all matches.
   */
  private List<ObjectNode> evaluateRules(
      DmnDecisionTableDTO table, VariableScope variables, int maxMatches) {
    List<ObjectNode> matched = new ArrayList<>();
    for (DmnRuleDTO rule : table.getRules()) {
      if (matched.size() >= maxMatches) {
        break;
      }
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
      if (!validateTypeRef(
          clause.getTypeRef(),
          inputValue,
          "Input expression '"
              + clause.getInputExpression()
              + "' in input clause '"
              + clause.getId()
              + "'")) {
        return false;
      }
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
      validateTypeRef(
          output.getTypeRef(),
          value,
          "Output clause '" + output.getName() + "' in decision table '" + rule.getId() + "'");
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
    String outputName = table.getOutputs().isEmpty() ? null : table.getOutputs().get(0).getName();

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
        yield min.isPresent()
            ? objectMapper.valueToTree(min.getAsDouble())
            : objectMapper.nullNode();
      }
      case MAX -> {
        var max = values.stream().mapToDouble(d -> d).max();
        yield max.isPresent()
            ? objectMapper.valueToTree(max.getAsDouble())
            : objectMapper.nullNode();
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
    String trimmed = expression.trim();
    String referenceCandidate = trimmed.startsWith("=") ? trimmed.substring(1).trim() : trimmed;
    ReferenceResolution directResolution = tryResolveSimpleReference(referenceCandidate, variables);
    if (directResolution.handled()) {
      return directResolution.value();
    }
    // Prefix with '=' to use the full FEEL expression evaluator (same as FeelExpressionHandlerImpl)
    String feelExpr = trimmed.startsWith("=") ? trimmed : "=" + trimmed;
    ParsedExpression parsed = getOrParseExpression(feelExpr);
    if (parsed == null) {
      return handleExpressionFailure("Failed to parse FEEL expression: " + expression, null);
    }
    EvaluationResult result = feelEngineApi.evaluate(parsed, buildContext(variables));
    if (result.isSuccess()) {
      Object val = ((SuccessfulEvaluationResult) result).productIterator().next();
      return objectMapper.valueToTree(val);
    }
    return handleExpressionFailure(
        "FEEL expression evaluation failed for '" + expression + "': " + result, null);
  }

  /**
   * Evaluates a FEEL unary test (e.g. {@code "Fall"}, {@code > 10}, {@code [1..5]}) against a given
   * input value using the FEEL {@code evaluateWithInput} API.
   */
  private boolean evaluateUnaryTest(
      String unaryTest, JsonNode inputValue, VariableScope variables) {
    ParsedExpression parsed = getOrParseUnaryTest(unaryTest.trim());
    if (parsed == null) {
      return handleUnaryFailure("Failed to parse FEEL unary test: " + unaryTest, null);
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
      return handleUnaryFailure(
          "FEEL unary test evaluation failed for '" + unaryTest + "': " + result, null);
    } catch (JsonProcessingException e) {
      return handleUnaryFailure("Failed to convert input value for FEEL unary test", e);
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
   * For single-output tables, returns the value directly. For multi-output tables, returns the full
   * output object.
   */
  private JsonNode flattenSingleOutput(ObjectNode row, DmnDecisionTableDTO table) {
    if (table.getOutputs() != null && table.getOutputs().size() == 1) {
      return row.get(table.getOutputs().get(0).getName());
    }
    return row;
  }

  private ReferenceResolution tryResolveSimpleReference(
      String expression, VariableScope variables) {
    if (expression == null || expression.isBlank()) {
      return ReferenceResolution.unhandled();
    }
    if ("true".equals(expression) || "false".equals(expression) || "null".equals(expression)) {
      return ReferenceResolution.unhandled();
    }

    Matcher matcher = SIMPLE_REFERENCE_PATTERN.matcher(expression);
    if (!matcher.matches()) {
      return ReferenceResolution.unhandled();
    }

    String baseName = matcher.group(1);
    String indexPart = matcher.group(2);
    String fieldPart = matcher.group(3);

    JsonNode current = variables.get(baseName);
    if (current == null) {
      return validationReferenceFailure("Variable '" + baseName + "' was not found");
    }

    if (indexPart != null) {
      if (!current.isArray()) {
        return validationReferenceFailure(
            "Variable '" + baseName + "' is not a list and cannot be indexed");
      }
      int oneBasedIndex = Integer.parseInt(indexPart);
      int zeroBasedIndex = oneBasedIndex - 1;
      if (zeroBasedIndex < 0 || zeroBasedIndex >= current.size()) {
        return validationReferenceFailure(
            "List variable '"
                + baseName
                + "' does not contain index "
                + oneBasedIndex
                + " (1-based)");
      }
      current = current.get(zeroBasedIndex);
    }

    if (fieldPart != null) {
      if (current == null || current.isNull()) {
        return validationReferenceFailure(
            "Reference '" + expression + "' points to null before field access");
      }
      if (current.isArray()) {
        return validationReferenceFailure(
            "Reference '"
                + expression
                + "' is invalid because list-valued results must be indexed before field access");
      }
      if (!current.isObject()) {
        return validationReferenceFailure(
            "Reference '"
                + expression
                + "' is invalid because field access requires a context/object");
      }
      JsonNode fieldValue = current.get(fieldPart);
      if (fieldValue == null) {
        return validationReferenceFailure(
            "Reference '"
                + expression
                + "' is invalid because field '"
                + fieldPart
                + "' does not exist");
      }
      current = fieldValue;
    }

    return ReferenceResolution.handled(current == null ? objectMapper.nullNode() : current);
  }

  private boolean validateTypeRef(String typeRef, JsonNode value, String location) {
    String normalizedTypeRef = normalizeTypeRef(typeRef);
    if (normalizedTypeRef == null || value == null || value.isNull()) {
      return true;
    }

    boolean valid =
        switch (normalizedTypeRef) {
          case "integer", "long" -> value.isIntegralNumber();
          case "double", "number", "decimal" -> value.isNumber();
          case "string" -> value.isTextual();
          case "boolean" -> value.isBoolean();
          case "context" -> value.isObject();
          case "list" -> value.isArray();
          case "any" -> true;
          default -> true;
        };

    if (!valid) {
      String message =
          location
              + " expected type '"
              + normalizedTypeRef
              + "' but got "
              + describeNodeType(value);
      handleValidationIssue(message);
      return false;
    }
    return true;
  }

  private String normalizeTypeRef(String typeRef) {
    if (typeRef == null || typeRef.isBlank()) {
      return null;
    }
    String normalized = typeRef.trim().toLowerCase();
    int namespaceSeparator = normalized.indexOf(':');
    return namespaceSeparator >= 0 ? normalized.substring(namespaceSeparator + 1) : normalized;
  }

  private String describeNodeType(JsonNode value) {
    if (value == null || value.isNull()) {
      return "null";
    }
    if (value.isObject()) {
      return "context/object";
    }
    if (value.isArray()) {
      return "list";
    }
    if (value.isTextual()) {
      return "string";
    }
    if (value.isIntegralNumber()) {
      return "integer";
    }
    if (value.isNumber()) {
      return "number";
    }
    if (value.isBoolean()) {
      return "boolean";
    }
    return value.getNodeType().name().toLowerCase();
  }

  private ReferenceResolution validationReferenceFailure(String message) {
    handleValidationIssue(message);
    return ReferenceResolution.handled(objectMapper.nullNode());
  }

  private JsonNode handleExpressionFailure(String message, Throwable cause) {
    return switch (effectiveValidationMode()) {
      case STRICT -> throw new DmnValidationException(message, cause);
      case WARN, PERMISSIVE -> {
        log.warn(message, cause);
        yield objectMapper.nullNode();
      }
    };
  }

  private boolean handleUnaryFailure(String message, Throwable cause) {
    return switch (effectiveValidationMode()) {
      case STRICT -> throw new DmnValidationException(message, cause);
      case WARN, PERMISSIVE -> {
        log.warn(message, cause);
        yield false;
      }
    };
  }

  private void handleValidationIssue(String message) {
    switch (effectiveValidationMode()) {
      case PERMISSIVE -> log.debug("DMN validation issue ignored in permissive mode: {}", message);
      case WARN -> log.warn("DMN validation issue: {}", message);
      case STRICT -> throw new DmnValidationException(message);
    }
  }

  private DmnValidationMode effectiveValidationMode() {
    if (globalConfigStore != null && globalConfigStore.get() != null) {
      DmnValidationMode runtimeMode = globalConfigStore.get().getDmnValidationMode();
      if (runtimeMode != null) {
        return runtimeMode;
      }
    }
    return configuredValidationMode;
  }

  private record ReferenceResolution(boolean handled, JsonNode value) {
    private static ReferenceResolution handled(JsonNode value) {
      return new ReferenceResolution(true, value);
    }

    private static ReferenceResolution unhandled() {
      return new ReferenceResolution(false, null);
    }
  }
}
