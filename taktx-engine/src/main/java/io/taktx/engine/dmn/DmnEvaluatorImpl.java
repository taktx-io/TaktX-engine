/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */

package io.taktx.engine.dmn;

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
import io.taktx.proto.VarList;
import io.taktx.proto.VariableValue;
import io.taktx.variables.Variables;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
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

  private static final Pattern SIMPLE_REFERENCE_PATTERN =
      Pattern.compile(
          "^([A-Za-z_][A-Za-z0-9_]*)(?:\\[(\\d+)\\])?(?:\\.([A-Za-z_][A-Za-z0-9_]*))?$");

  private final FeelEngineApi feelEngineApi;

  /**
   * Optional: resolves required decisions from the deployed store to support DRG chaining. {@code
   * null} in unit-test contexts where no Kafka store is available.
   */
  private final DmnDecisionResolver decisionResolver;

  private final GlobalConfigStore globalConfigStore;
  private final DmnValidationMode configuredValidationMode;

  /** Thread-safe cache of parsed FEEL expressions for performance. */
  private final Map<String, ParsedExpression> expressionCache = new ConcurrentHashMap<>();

  private BuiltinFunctions builtinFunctions;

  /** CDI constructor used in production. */
  @Inject
  public DmnEvaluatorImpl(
      FeelEngineApi feelEngineApi,
      DmnDecisionResolver decisionResolver,
      GlobalConfigStore globalConfigStore,
      TaktConfiguration taktConfiguration) {
    this(
        feelEngineApi,
        decisionResolver,
        globalConfigStore,
        taktConfiguration != null
            ? taktConfiguration.getDmnValidationMode()
            : DmnValidationMode.PERMISSIVE);
  }

  private DmnEvaluatorImpl(
      FeelEngineApi feelEngineApi,
      DmnDecisionResolver decisionResolver,
      GlobalConfigStore globalConfigStore,
      DmnValidationMode configuredValidationMode) {
    this.feelEngineApi = feelEngineApi;
    this.decisionResolver = decisionResolver;
    this.globalConfigStore = globalConfigStore;
    this.configuredValidationMode =
        configuredValidationMode != null ? configuredValidationMode : DmnValidationMode.PERMISSIVE;
  }

  /** Convenience constructor for unit tests — DRG chaining is disabled (resolver is null). */
  public DmnEvaluatorImpl(FeelEngineApi feelEngineApi) {
    this(feelEngineApi, null, null, DmnValidationMode.PERMISSIVE);
  }

  /** Convenience constructor for unit tests with DRG chaining support. */
  public DmnEvaluatorImpl(FeelEngineApi feelEngineApi, DmnDecisionResolver decisionResolver) {
    this(feelEngineApi, decisionResolver, null, DmnValidationMode.PERMISSIVE);
  }

  /** Convenience constructor for unit tests with an explicit validation mode. */
  public DmnEvaluatorImpl(
      FeelEngineApi feelEngineApi,
      DmnDecisionResolver decisionResolver,
      DmnValidationMode validationMode) {
    this(feelEngineApi, decisionResolver, null, validationMode);
  }

  @Override
  public VariableValue evaluate(DmnDecisionDTO decision, VariableScope variables) {
    return evaluateDecision(decision, variables, new HashMap<>(), new LinkedHashSet<>());
  }

  private VariableValue evaluateDecision(
      DmnDecisionDTO decision,
      VariableScope variables,
      Map<String, VariableValue> drgResults,
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

  private VariableScope createDrgOverlayScope(
      VariableScope base, Map<String, VariableValue> drgResults) {
    VariableScope overlay = new VariableScope(base, null, null, null);
    overlay.merge(drgResults);
    return overlay;
  }

  private VariableValue evaluateLiteralExpression(
      DmnLiteralExpressionDTO literalExpression, VariableScope variables) {
    String expr = literalExpression.getExpression();
    if (expr == null || expr.isBlank()) {
      return Variables.nullValue();
    }
    return evaluateFeelOutputExpression(expr, variables);
  }

  private VariableValue evaluateDecisionTable(DmnDecisionTableDTO table, VariableScope variables) {
    DmnHitPolicy hitPolicy =
        table.getHitPolicy() != null ? table.getHitPolicy() : DmnHitPolicy.UNIQUE;

    int maxMatches =
        switch (hitPolicy) {
          case FIRST, ANY, PRIORITY -> 1;
          case UNIQUE -> 2;
          default -> Integer.MAX_VALUE;
        };

    List<Map<String, VariableValue>> matchedRows = evaluateRules(table, variables, maxMatches);

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

  private List<Map<String, VariableValue>> evaluateRules(
      DmnDecisionTableDTO table, VariableScope variables, int maxMatches) {
    List<Map<String, VariableValue>> matched = new ArrayList<>();
    for (DmnRuleDTO rule : table.getRules()) {
      if (matched.size() >= maxMatches) {
        break;
      }
      if (inputsMatch(table.getInputs(), rule, variables)) {
        matched.add(buildOutputRow(table.getOutputs(), rule, variables));
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
        continue;
      }
      DmnInputClauseDTO clause = inputs.get(i);
      VariableValue inputValue =
          evaluateFeelOutputExpression(clause.getInputExpression(), variables);
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

  private Map<String, VariableValue> buildOutputRow(
      List<DmnOutputClauseDTO> outputs, DmnRuleDTO rule, VariableScope variables) {
    Map<String, VariableValue> result = new LinkedHashMap<>();
    List<String> outputEntries = rule.getOutputEntries();
    for (int i = 0; i < outputs.size(); i++) {
      DmnOutputClauseDTO output = outputs.get(i);
      String entry = i < outputEntries.size() ? outputEntries.get(i) : "";
      VariableValue value =
          (entry == null || entry.isBlank())
              ? Variables.nullValue()
              : evaluateFeelOutputExpression(entry, variables);
      validateTypeRef(
          output.getTypeRef(),
          value,
          "Output clause '" + output.getName() + "' in decision table '" + rule.getId() + "'");
      result.put(output.getName(), normalize(value));
    }
    return result;
  }

  private VariableValue handleUnique(
      List<Map<String, VariableValue>> matched, DmnDecisionTableDTO table) {
    if (matched.isEmpty()) {
      return Variables.nullValue();
    }
    if (matched.size() > 1) {
      log.warn(
          "UNIQUE hit policy violated for decision table '{}': {} rules matched",
          table.getId(),
          matched.size());
    }
    return flattenSingleOutput(matched.get(0), table);
  }

  private VariableValue handleFirst(
      List<Map<String, VariableValue>> matched, DmnDecisionTableDTO table) {
    if (matched.isEmpty()) {
      return Variables.nullValue();
    }
    return flattenSingleOutput(matched.getFirst(), table);
  }

  private VariableValue handleAny(
      List<Map<String, VariableValue>> matched, DmnDecisionTableDTO table) {
    if (matched.isEmpty()) {
      return Variables.nullValue();
    }
    return flattenSingleOutput(matched.getFirst(), table);
  }

  private VariableValue handleRuleOrder(
      List<Map<String, VariableValue>> matched, DmnDecisionTableDTO table) {
    if (matched.isEmpty()) {
      return Variables.nullValue();
    }
    List<VariableValue> values = new ArrayList<>(matched.size());
    if (table.getOutputs().size() == 1) {
      String name = table.getOutputs().getFirst().getName();
      matched.forEach(row -> values.add(normalize(row.get(name))));
    } else {
      matched.forEach(row -> values.add(mapValue(row)));
    }
    return listValue(values);
  }

  private VariableValue handleOutputOrder(
      List<Map<String, VariableValue>> matched, DmnDecisionTableDTO table) {
    return handleRuleOrder(matched, table);
  }

  private VariableValue handlePriority(
      List<Map<String, VariableValue>> matched, DmnDecisionTableDTO table) {
    return handleFirst(matched, table);
  }

  private VariableValue handleCollect(
      List<Map<String, VariableValue>> matched, DmnDecisionTableDTO table) {
    DmnCollectOperator op =
        table.getCollectOperator() != null ? table.getCollectOperator() : DmnCollectOperator.NONE;

    if (matched.isEmpty()) {
      return Variables.nullValue();
    }

    if (op == DmnCollectOperator.NONE) {
      return handleRuleOrder(matched, table);
    }

    String outputName =
        table.getOutputs().isEmpty() ? null : table.getOutputs().getFirst().getName();

    List<Double> values = new ArrayList<>();
    for (Map<String, VariableValue> row : matched) {
      VariableValue value = outputName != null ? row.get(outputName) : null;
      if (value != null) {
        switch (value.getKindCase()) {
          case LONG_VALUE -> values.add((double) value.getLongValue());
          case DOUBLE_VALUE -> values.add(value.getDoubleValue());
          default -> {
            // Ignore non-numeric collect values to match the previous permissive legacy handling.
          }
        }
      }
    }

    return switch (op) {
      case SUM -> Variables.of(values.stream().mapToDouble(d -> d).sum());
      case MIN -> {
        var min = values.stream().mapToDouble(d -> d).min();
        yield min.isPresent() ? Variables.of(min.getAsDouble()) : Variables.nullValue();
      }
      case MAX -> {
        var max = values.stream().mapToDouble(d -> d).max();
        yield max.isPresent() ? Variables.of(max.getAsDouble()) : Variables.nullValue();
      }
      case COUNT -> Variables.of((double) matched.size());
      default -> handleRuleOrder(matched, table);
    };
  }

  private VariableValue evaluateFeelOutputExpression(String expression, VariableScope variables) {
    if (expression == null || expression.isBlank()) {
      return Variables.nullValue();
    }
    String trimmed = expression.trim();
    String referenceCandidate = trimmed.startsWith("=") ? trimmed.substring(1).trim() : trimmed;
    ReferenceResolution directResolution = tryResolveSimpleReference(referenceCandidate, variables);
    if (directResolution.handled()) {
      return directResolution.value();
    }
    String feelExpr = trimmed.startsWith("=") ? trimmed : "=" + trimmed;
    ParsedExpression parsed = getOrParseExpression(feelExpr);
    if (parsed == null) {
      return handleExpressionFailure("Failed to parse FEEL expression: " + expression, null);
    }
    EvaluationResult result = feelEngineApi.evaluate(parsed, buildContext(variables));
    if (result.isSuccess()) {
      Object val = ((SuccessfulEvaluationResult) result).productIterator().next();
      return Variables.of(val);
    }
    return handleExpressionFailure(
        "FEEL expression evaluation failed for '" + expression + "': " + result, null);
  }

  private boolean evaluateUnaryTest(
      String unaryTest, VariableValue inputValue, VariableScope variables) {
    ParsedExpression parsed = getOrParseUnaryTest(unaryTest.trim());
    if (parsed == null) {
      return handleUnaryFailure("Failed to parse FEEL unary test: " + unaryTest, null);
    }
    EvaluationResult result =
        feelEngineApi.evaluateWithInput(
            parsed, Variables.toJavaObject(inputValue), buildContext(variables));
    if (result.isSuccess()) {
      Object val = ((SuccessfulEvaluationResult) result).result();
      return Boolean.TRUE.equals(val);
    }
    return handleUnaryFailure(
        "FEEL unary test evaluation failed for '" + unaryTest + "': " + result, null);
  }

  private ParsedExpression getOrParseExpression(String feelExpr) {
    ParsedExpression cached = expressionCache.get(feelExpr);
    if (cached != null) {
      return cached;
    }
    ParseResult pr = feelEngineApi.parseExpression(feelExpr.substring(1));
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
            VariableValue value = variables.get(name);
            if (value == null) {
              return Option.empty();
            }
            return Option.apply(Variables.toJavaObject(value));
          }

          @Override
          public Iterable<String> keys() {
            return CollectionConverters.SetHasAsScala(variables.getVariables().keySet()).asScala();
          }
        };
      }

      @Override
      public FunctionProvider functionProvider() {
        return getBuiltinFunctions();
      }
    };
  }

  private synchronized BuiltinFunctions getBuiltinFunctions() {
    if (builtinFunctions == null) {
      builtinFunctions =
          new BuiltinFunctions(SystemClock$.MODULE$, ValueMapper.defaultValueMapper());
    }
    return builtinFunctions;
  }

  private VariableValue flattenSingleOutput(
      Map<String, VariableValue> row, DmnDecisionTableDTO table) {
    if (table.getOutputs() != null && table.getOutputs().size() == 1) {
      return normalize(row.get(table.getOutputs().getFirst().getName()));
    }
    return mapValue(row);
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

    VariableValue current = variables.get(baseName);
    if (current == null) {
      return validationReferenceFailure("Variable '" + baseName + "' was not found");
    }

    if (indexPart != null) {
      if (current.getKindCase() != VariableValue.KindCase.LIST_VALUE) {
        return validationReferenceFailure(
            "Variable '" + baseName + "' is not a list and cannot be indexed");
      }
      int oneBasedIndex = Integer.parseInt(indexPart);
      int zeroBasedIndex = oneBasedIndex - 1;
      if (zeroBasedIndex < 0 || zeroBasedIndex >= current.getListValue().getItemsCount()) {
        return validationReferenceFailure(
            "List variable '"
                + baseName
                + "' does not contain index "
                + oneBasedIndex
                + " (1-based)");
      }
      current = current.getListValue().getItems(zeroBasedIndex);
    }

    if (fieldPart != null) {
      if (isNullish(current)) {
        return validationReferenceFailure(
            "Reference '" + expression + "' points to null before field access");
      }
      if (current.getKindCase() == VariableValue.KindCase.LIST_VALUE) {
        return validationReferenceFailure(
            "Reference '"
                + expression
                + "' is invalid because list-valued results must be indexed before field access");
      }
      if (current.getKindCase() != VariableValue.KindCase.MAP_VALUE) {
        return validationReferenceFailure(
            "Reference '"
                + expression
                + "' is invalid because field access requires a context/object");
      }
      VariableValue fieldValue = current.getMapValue().getEntriesMap().get(fieldPart);
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

    return ReferenceResolution.handled(normalize(current));
  }

  private boolean validateTypeRef(String typeRef, VariableValue value, String location) {
    String normalizedTypeRef = normalizeTypeRef(typeRef);
    if (normalizedTypeRef == null || isNullish(value)) {
      return true;
    }

    boolean valid =
        switch (normalizedTypeRef) {
          case "integer", "long" -> value.getKindCase() == VariableValue.KindCase.LONG_VALUE;
          case "double", "number", "decimal" ->
              value.getKindCase() == VariableValue.KindCase.LONG_VALUE
                  || value.getKindCase() == VariableValue.KindCase.DOUBLE_VALUE;
          case "string" -> value.getKindCase() == VariableValue.KindCase.STRING_VALUE;
          case "boolean" -> value.getKindCase() == VariableValue.KindCase.BOOL_VALUE;
          case "context" -> value.getKindCase() == VariableValue.KindCase.MAP_VALUE;
          case "list" -> value.getKindCase() == VariableValue.KindCase.LIST_VALUE;
          case "any" -> true;
          default -> true;
        };

    if (!valid) {
      String message =
          location
              + " expected type '"
              + normalizedTypeRef
              + "' but got "
              + describeValueType(value);
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

  private String describeValueType(VariableValue value) {
    if (isNullish(value)) {
      return "null";
    }
    return switch (value.getKindCase()) {
      case MAP_VALUE -> "context/object";
      case LIST_VALUE -> "list";
      case STRING_VALUE -> "string";
      case LONG_VALUE -> "integer";
      case DOUBLE_VALUE -> "number";
      case BOOL_VALUE -> "boolean";
      case BYTES_VALUE -> "bytes";
      case NULL_VALUE, KIND_NOT_SET -> "null";
    };
  }

  private ReferenceResolution validationReferenceFailure(String message) {
    handleValidationIssue(message);
    return ReferenceResolution.handled(Variables.nullValue());
  }

  private VariableValue handleExpressionFailure(String message, Throwable cause) {
    return switch (effectiveValidationMode()) {
      case STRICT -> throw new DmnValidationException(message, cause);
      case WARN, PERMISSIVE -> {
        log.warn(message, cause);
        yield Variables.nullValue();
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

  private static VariableValue normalize(VariableValue value) {
    return value == null ? Variables.nullValue() : value;
  }

  private static boolean isNullish(VariableValue value) {
    return value == null
        || value.getKindCase() == VariableValue.KindCase.NULL_VALUE
        || value.getKindCase() == VariableValue.KindCase.KIND_NOT_SET;
  }

  private static VariableValue mapValue(Map<String, VariableValue> row) {
    return VariableValue.newBuilder().setMapValue(Variables.toVarMap(row)).build();
  }

  private static VariableValue listValue(List<VariableValue> values) {
    VarList.Builder builder = VarList.newBuilder();
    values.forEach(value -> builder.addItems(normalize(value)));
    return VariableValue.newBuilder().setListValue(builder.build()).build();
  }

  private record ReferenceResolution(boolean handled, VariableValue value) {
    private static ReferenceResolution handled(VariableValue value) {
      return new ReferenceResolution(true, value);
    }

    private static ReferenceResolution unhandled() {
      return new ReferenceResolution(false, null);
    }
  }
}
