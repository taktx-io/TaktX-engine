/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */

package io.taktx.engine.dmn;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.DoubleNode;
import com.fasterxml.jackson.databind.node.IntNode;
import com.fasterxml.jackson.databind.node.TextNode;
import io.taktx.dto.DmnCollectOperator;
import io.taktx.dto.DmnDecisionDTO;
import io.taktx.dto.DmnDecisionTableDTO;
import io.taktx.dto.DmnHitPolicy;
import io.taktx.dto.DmnInputClauseDTO;
import io.taktx.dto.DmnLiteralExpressionDTO;
import io.taktx.dto.DmnOutputClauseDTO;
import io.taktx.dto.DmnRuleDTO;
import io.taktx.engine.feel.FeelEngineProvider;
import io.taktx.engine.pi.model.VariableScope;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class DmnEvaluatorImplTest {

  private DmnEvaluatorImpl evaluator;
  private VariableScope variables;

  @BeforeEach
  void setUp() {
    evaluator = new DmnEvaluatorImpl(FeelEngineProvider.FEEL_ENGINE_API, new ObjectMapper());
    variables = VariableScope.empty(null, null);
  }

  // ── helper builders ───────────────────────────────────────────────────────

  /** id, label, expr, typeRef */
  private static DmnInputClauseDTO input(String expr) {
    return new DmnInputClauseDTO(null, null, expr, null);
  }

  /** id, label, name, typeRef */
  private static DmnOutputClauseDTO output(String name) {
    return new DmnOutputClauseDTO(null, null, name, null);
  }

  /** id, inputEntries, outputEntries */
  private static DmnRuleDTO rule(List<String> inputs, List<String> outputs) {
    return new DmnRuleDTO(null, inputs, outputs);
  }

  private static DmnDecisionTableDTO table(
      DmnHitPolicy policy,
      List<DmnInputClauseDTO> ins,
      List<DmnOutputClauseDTO> outs,
      List<DmnRuleDTO> rules) {
    return new DmnDecisionTableDTO("dt", policy, null, ins, outs, rules);
  }

  private static DmnDecisionDTO decisionWithTable(DmnDecisionTableDTO dt) {
    return new DmnDecisionDTO("decision", null, dt, null, null);
  }

  // ── UNIQUE hit policy ────────────────────────────────────────────────────

  @Test
  void unique_singleMatch_returnsOutputValue() {
    variables.put("category", new TextNode("Premium"));
    DmnDecisionTableDTO dt =
        table(
            DmnHitPolicy.UNIQUE,
            List.of(input("category")),
            List.of(output("discount")),
            List.of(
                rule(List.of("\"Premium\""), List.of("0.2")),
                rule(List.of("\"Standard\""), List.of("0.1"))));

    JsonNode result = evaluator.evaluate(decisionWithTable(dt), variables);

    assertThat(result.doubleValue()).isEqualTo(0.2);
  }

  @Test
  void unique_noMatch_returnsNull() {
    variables.put("category", new TextNode("Unknown"));
    DmnDecisionTableDTO dt =
        table(
            DmnHitPolicy.UNIQUE,
            List.of(input("category")),
            List.of(output("discount")),
            List.of(rule(List.of("\"Premium\""), List.of("0.2"))));

    JsonNode result = evaluator.evaluate(decisionWithTable(dt), variables);

    assertThat(result.isNull()).isTrue();
  }

  @Test
  void unique_wildcardInput_alwaysMatches() {
    variables.put("x", new IntNode(99));
    DmnDecisionTableDTO dt =
        table(
            DmnHitPolicy.UNIQUE,
            List.of(input("x")),
            List.of(output("result")),
            List.of(rule(List.of(""), List.of("\"any\""))));

    JsonNode result = evaluator.evaluate(decisionWithTable(dt), variables);

    assertThat(result.asText()).isEqualTo("any");
  }

  // ── FIRST hit policy ─────────────────────────────────────────────────────

  @Test
  void first_multipleMatches_returnsFirst() {
    variables.put("val", new IntNode(5));
    DmnDecisionTableDTO dt =
        table(
            DmnHitPolicy.FIRST,
            List.of(input("val")),
            List.of(output("label")),
            List.of(
                rule(List.of(">3"), List.of("\"high\"")),
                rule(List.of(">1"), List.of("\"medium\""))));

    JsonNode result = evaluator.evaluate(decisionWithTable(dt), variables);

    assertThat(result.asText()).isEqualTo("high");
  }

  // ── ANY hit policy ────────────────────────────────────────────────────────

  @Test
  void any_returnsFirstMatchedRow() {
    variables.put("active", new TextNode("true"));
    DmnDecisionTableDTO dt =
        table(
            DmnHitPolicy.ANY,
            List.of(input("active")),
            List.of(output("label")),
            List.of(
                rule(List.of("\"true\""), List.of("\"yes\"")),
                rule(List.of("\"true\""), List.of("\"yes\""))));

    JsonNode result = evaluator.evaluate(decisionWithTable(dt), variables);

    assertThat(result.asText()).isEqualTo("yes");
  }

  // ── RULE_ORDER hit policy ─────────────────────────────────────────────────

  @Test
  void ruleOrder_allMatchingRules_returnsArray() {
    variables.put("n", new IntNode(10));
    DmnDecisionTableDTO dt =
        table(
            DmnHitPolicy.RULE_ORDER,
            List.of(input("n")),
            List.of(output("label")),
            List.of(
                rule(List.of(">5"), List.of("\"big\"")),
                rule(List.of(">2"), List.of("\"medium\""))));

    JsonNode result = evaluator.evaluate(decisionWithTable(dt), variables);

    assertThat(result.isArray()).isTrue();
    assertThat(result.size()).isEqualTo(2);
    assertThat(result.get(0).asText()).isEqualTo("big");
    assertThat(result.get(1).asText()).isEqualTo("medium");
  }

  // ── COLLECT SUM hit policy ───────────────────────────────────────────────

  @Test
  void collectSum_sumsOutputValues() {
    variables.put("active", new TextNode("yes"));
    DmnDecisionTableDTO dt =
        new DmnDecisionTableDTO(
            "dt",
            DmnHitPolicy.COLLECT,
            DmnCollectOperator.SUM,
            List.of(input("active")),
            List.of(output("amount")),
            List.of(
                rule(List.of("\"yes\""), List.of("10")), rule(List.of("\"yes\""), List.of("20"))));

    JsonNode result = evaluator.evaluate(decisionWithTable(dt), variables);

    assertThat(result.doubleValue()).isEqualTo(30.0);
  }

  @Test
  void collectCount_countsMatchingRows() {
    variables.put("active", new TextNode("yes"));
    DmnDecisionTableDTO dt =
        new DmnDecisionTableDTO(
            "dt",
            DmnHitPolicy.COLLECT,
            DmnCollectOperator.COUNT,
            List.of(input("active")),
            List.of(output("amount")),
            List.of(
                rule(List.of("\"yes\""), List.of("10")), rule(List.of("\"yes\""), List.of("20"))));

    JsonNode result = evaluator.evaluate(decisionWithTable(dt), variables);

    assertThat(result.doubleValue()).isEqualTo(2.0);
  }

  @Test
  void collectMin_returnsMinValue() {
    variables.put("active", new TextNode("yes"));
    DmnDecisionTableDTO dt =
        new DmnDecisionTableDTO(
            "dt",
            DmnHitPolicy.COLLECT,
            DmnCollectOperator.MIN,
            List.of(input("active")),
            List.of(output("amount")),
            List.of(
                rule(List.of("\"yes\""), List.of("5")), rule(List.of("\"yes\""), List.of("3"))));

    JsonNode result = evaluator.evaluate(decisionWithTable(dt), variables);

    assertThat(result.doubleValue()).isEqualTo(3.0);
  }

  @Test
  void collectMax_returnsMaxValue() {
    variables.put("active", new TextNode("yes"));
    DmnDecisionTableDTO dt =
        new DmnDecisionTableDTO(
            "dt",
            DmnHitPolicy.COLLECT,
            DmnCollectOperator.MAX,
            List.of(input("active")),
            List.of(output("amount")),
            List.of(
                rule(List.of("\"yes\""), List.of("5")), rule(List.of("\"yes\""), List.of("3"))));

    JsonNode result = evaluator.evaluate(decisionWithTable(dt), variables);

    assertThat(result.doubleValue()).isEqualTo(5.0);
  }

  @Test
  void collectNone_returnsArray() {
    variables.put("active", new TextNode("yes"));
    DmnDecisionTableDTO dt =
        new DmnDecisionTableDTO(
            "dt",
            DmnHitPolicy.COLLECT,
            DmnCollectOperator.NONE,
            List.of(input("active")),
            List.of(output("amount")),
            List.of(
                rule(List.of("\"yes\""), List.of("5")), rule(List.of("\"yes\""), List.of("3"))));

    JsonNode result = evaluator.evaluate(decisionWithTable(dt), variables);

    assertThat(result.isArray()).isTrue();
    assertThat(result.size()).isEqualTo(2);
  }

  // ── PRIORITY hit policy ────────────────────────────────────────────────────

  @Test
  void priority_returnsFirstMatchedRow() {
    variables.put("status", new TextNode("Gold"));
    DmnDecisionTableDTO dt =
        table(
            DmnHitPolicy.PRIORITY,
            List.of(input("status")),
            List.of(output("discount")),
            List.of(
                rule(List.of("\"Gold\""), List.of("0.3")),
                rule(List.of("\"Gold\""), List.of("0.2"))));

    JsonNode result = evaluator.evaluate(decisionWithTable(dt), variables);

    assertThat(result.doubleValue()).isEqualTo(0.3);
  }

  // ── OUTPUT_ORDER hit policy ────────────────────────────────────────────────

  @Test
  void outputOrder_allMatchingRules_returnsArrayInDefinedOrder() {
    variables.put("n", new IntNode(10));
    DmnDecisionTableDTO dt =
        table(
            DmnHitPolicy.OUTPUT_ORDER,
            List.of(input("n")),
            List.of(output("label")),
            List.of(
                rule(List.of(">5"), List.of("\"big\"")),
                rule(List.of(">2"), List.of("\"medium\""))));

    JsonNode result = evaluator.evaluate(decisionWithTable(dt), variables);

    assertThat(result.isArray()).isTrue();
    assertThat(result.size()).isEqualTo(2);
    assertThat(result.get(0).asText()).isEqualTo("big");
    assertThat(result.get(1).asText()).isEqualTo("medium");
  }

  @Test
  void outputOrder_noMatch_returnsNull() {
    variables.put("n", new IntNode(1));
    DmnDecisionTableDTO dt =
        table(
            DmnHitPolicy.OUTPUT_ORDER,
            List.of(input("n")),
            List.of(output("label")),
            List.of(rule(List.of(">5"), List.of("\"big\""))));

    JsonNode result = evaluator.evaluate(decisionWithTable(dt), variables);

    assertThat(result.isNull()).isTrue();
  }

  // ── Multi-output columns ───────────────────────────────────────────────────

  @Test
  void multipleOutputColumns_returnsObjectNode() {
    variables.put("tier", new TextNode("Gold"));
    DmnDecisionTableDTO dt =
        table(
            DmnHitPolicy.UNIQUE,
            List.of(input("tier")),
            List.of(output("discount"), output("limit")),
            List.of(rule(List.of("\"Gold\""), List.of("0.3", "1000"))));

    JsonNode result = evaluator.evaluate(decisionWithTable(dt), variables);

    assertThat(result.isObject()).isTrue();
    assertThat(result.get("discount").doubleValue()).isEqualTo(0.3);
    assertThat(result.get("limit").doubleValue()).isEqualTo(1000.0);
  }

  // ── Multiple input columns ─────────────────────────────────────────────────

  @Test
  void multipleInputColumns_allMatch_returnsOutput() {
    variables.put("tier", new TextNode("Gold"));
    variables.put("active", new TextNode("yes"));
    DmnDecisionTableDTO dt =
        table(
            DmnHitPolicy.UNIQUE,
            List.of(input("tier"), input("active")),
            List.of(output("discount")),
            List.of(rule(List.of("\"Gold\"", "\"yes\""), List.of("0.3"))));

    JsonNode result = evaluator.evaluate(decisionWithTable(dt), variables);

    assertThat(result.doubleValue()).isEqualTo(0.3);
  }

  @Test
  void multipleInputColumns_secondInputDoesNotMatch_returnsNull() {
    variables.put("tier", new TextNode("Gold"));
    variables.put("active", new TextNode("no"));
    DmnDecisionTableDTO dt =
        table(
            DmnHitPolicy.UNIQUE,
            List.of(input("tier"), input("active")),
            List.of(output("discount")),
            List.of(rule(List.of("\"Gold\"", "\"yes\""), List.of("0.3"))));

    JsonNode result = evaluator.evaluate(decisionWithTable(dt), variables);

    assertThat(result.isNull()).isTrue();
  }

  // ── not() unary-test expressions ──────────────────────────────────────────

  @Test
  void notUnaryTest_excludesSingleValue_matchWhenDifferent() {
    variables.put("tier", new TextNode("Silver"));
    DmnDecisionTableDTO dt =
        table(
            DmnHitPolicy.UNIQUE,
            List.of(input("tier")),
            List.of(output("result")),
            List.of(rule(List.of("not(\"Gold\")"), List.of("\"non-gold\""))));

    JsonNode result = evaluator.evaluate(decisionWithTable(dt), variables);

    assertThat(result.asText()).isEqualTo("non-gold");
  }

  @Test
  void notUnaryTest_excludesTwoValues_matchWhenNeitherExcluded() {
    variables.put("tier", new TextNode("Bronze"));
    DmnDecisionTableDTO dt =
        table(
            DmnHitPolicy.UNIQUE,
            List.of(input("tier")),
            List.of(output("result")),
            List.of(rule(List.of("not(\"Gold\",\"Silver\")"), List.of("\"other\""))));

    JsonNode result = evaluator.evaluate(decisionWithTable(dt), variables);

    assertThat(result.asText()).isEqualTo("other");
  }

  @Test
  void notUnaryTest_excludesTwoValues_noMatchWhenInputIsExcluded() {
    variables.put("tier", new TextNode("Gold"));
    DmnDecisionTableDTO dt =
        table(
            DmnHitPolicy.UNIQUE,
            List.of(input("tier")),
            List.of(output("result")),
            List.of(rule(List.of("not(\"Gold\",\"Silver\")"), List.of("\"other\""))));

    JsonNode result = evaluator.evaluate(decisionWithTable(dt), variables);

    assertThat(result.isNull()).isTrue();
  }

  // ── FEEL expressions in output entries ────────────────────────────────────

  @Test
  void outputFeelExpression_arithmeticOnVariable_returnsComputedValue() {
    // Output entry is a FEEL expression referencing an input variable, not a plain literal
    variables.put("basePrice", new DoubleNode(100.0));
    variables.put("tier", new TextNode("Gold"));
    DmnDecisionTableDTO dt =
        table(
            DmnHitPolicy.UNIQUE,
            List.of(input("tier")),
            List.of(output("finalPrice")),
            List.of(rule(List.of("\"Gold\""), List.of("basePrice * 0.7"))));

    JsonNode result = evaluator.evaluate(decisionWithTable(dt), variables);

    assertThat(result.doubleValue()).isEqualTo(70.0);
  }

  @Test
  void outputFeelExpression_conditionalExpression_returnsCorrectBranch() {
    variables.put("score", new IntNode(85));
    DmnDecisionTableDTO dt =
        table(
            DmnHitPolicy.UNIQUE,
            List.of(input("score")),
            List.of(output("grade")),
            List.of(rule(List.of(">= 0"), List.of("if score >= 90 then \"A\" else \"B\""))));

    JsonNode result = evaluator.evaluate(decisionWithTable(dt), variables);

    assertThat(result.asText()).isEqualTo("B");
  }

  // ── Literal expression decision ────────────────────────────────────────────

  @Test
  void literalExpression_evaluatesFeelExpression() {
    variables.put("x", new IntNode(5));
    DmnLiteralExpressionDTO le = new DmnLiteralExpressionDTO(null, "=x * 2", null);
    DmnDecisionDTO d = new DmnDecisionDTO("decision", null, null, le, null);

    JsonNode result = evaluator.evaluate(d, variables);

    assertThat(result.intValue()).isEqualTo(10);
  }

  // ── Error path ─────────────────────────────────────────────────────────────

  @Test
  void noTableOrLiteral_throws() {
    DmnDecisionDTO d = new DmnDecisionDTO("decision", null, null, null, null);

    assertThatThrownBy(() -> evaluator.evaluate(d, variables))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("decision");
  }

  // ── DRG chaining ───────────────────────────────────────────────────────────

  @Test
  void drg_requiredDecisionResultIsVisibleToRoot() {
    // Arrange: categoryDecision (no deps) → "Premium"
    DmnDecisionTableDTO categoryTable =
        table(
            DmnHitPolicy.FIRST,
            List.of(input("loyaltyPoints")),
            List.of(output("category")),
            List.of(
                rule(List.of(">= 1000"), List.of("\"Premium\"")),
                rule(List.of(">= 500"), List.of("\"Standard\"")),
                rule(List.of(""), List.of("\"Basic\""))));
    DmnDecisionDTO categoryDecision =
        new DmnDecisionDTO("categoryDecision", null, categoryTable, null, null);

    // discountDecision requires categoryDecision; reads variable "categoryDecision"
    DmnDecisionTableDTO discountTable =
        table(
            DmnHitPolicy.FIRST,
            List.of(input("categoryDecision")),
            List.of(output("discount")),
            List.of(
                rule(List.of("\"Premium\""), List.of("0.2")),
                rule(List.of("\"Standard\""), List.of("0.1")),
                rule(List.of(""), List.of("0.05"))));
    DmnDecisionDTO discountDecision =
        new DmnDecisionDTO(
            "discountDecision", null, discountTable, null, List.of("categoryDecision"));

    DmnDecisionResolver resolver = mock(DmnDecisionResolver.class);
    when(resolver.resolve("categoryDecision")).thenReturn(Optional.of(categoryDecision));

    DmnEvaluatorImpl drgEvaluator =
        new DmnEvaluatorImpl(FeelEngineProvider.FEEL_ENGINE_API, new ObjectMapper(), resolver);

    variables.put("loyaltyPoints", new IntNode(1500));
    JsonNode result = drgEvaluator.evaluate(discountDecision, variables);
    assertThat(result.doubleValue()).isEqualTo(0.2);

    variables.put("loyaltyPoints", new IntNode(750));
    result = drgEvaluator.evaluate(discountDecision, variables);
    assertThat(result.doubleValue()).isEqualTo(0.1);

    variables.put("loyaltyPoints", new IntNode(100));
    result = drgEvaluator.evaluate(discountDecision, variables);
    assertThat(result.doubleValue()).isEqualTo(0.05);
  }

  @Test
  void drg_requiredDecisionResultDoesNotLeakIntoProcessScope() {
    DmnDecisionTableDTO categoryTable =
        table(
            DmnHitPolicy.UNIQUE,
            List.of(input("loyaltyPoints")),
            List.of(output("category")),
            List.of(rule(List.of(""), List.of("\"Gold\""))));
    DmnDecisionDTO categoryDecision =
        new DmnDecisionDTO("categoryDecision", null, categoryTable, null, null);

    DmnDecisionTableDTO discountTable =
        table(
            DmnHitPolicy.UNIQUE,
            List.of(input("categoryDecision")),
            List.of(output("discount")),
            List.of(rule(List.of("\"Gold\""), List.of("0.3"))));
    DmnDecisionDTO discountDecision =
        new DmnDecisionDTO(
            "discountDecision", null, discountTable, null, List.of("categoryDecision"));

    DmnDecisionResolver resolver = mock(DmnDecisionResolver.class);
    when(resolver.resolve("categoryDecision")).thenReturn(Optional.of(categoryDecision));

    DmnEvaluatorImpl drgEvaluator =
        new DmnEvaluatorImpl(FeelEngineProvider.FEEL_ENGINE_API, new ObjectMapper(), resolver);

    variables.put("loyaltyPoints", new IntNode(999));
    drgEvaluator.evaluate(discountDecision, variables);

    // The intermediate DRG result must not have been written into the process variable scope
    assertThat(variables.get("categoryDecision")).isNull();
  }

  @Test
  void drg_circularDependency_throws() {
    // A requires B, B requires A
    DmnDecisionDTO decisionA =
        new DmnDecisionDTO(
            "A",
            null,
            table(DmnHitPolicy.UNIQUE, List.of(), List.of(), List.of()),
            null,
            List.of("B"));
    DmnDecisionDTO decisionB =
        new DmnDecisionDTO(
            "B",
            null,
            table(DmnHitPolicy.UNIQUE, List.of(), List.of(), List.of()),
            null,
            List.of("A"));

    DmnDecisionResolver resolver = mock(DmnDecisionResolver.class);
    when(resolver.resolve("B")).thenReturn(Optional.of(decisionB));
    when(resolver.resolve("A")).thenReturn(Optional.of(decisionA));

    DmnEvaluatorImpl drgEvaluator =
        new DmnEvaluatorImpl(FeelEngineProvider.FEEL_ENGINE_API, new ObjectMapper(), resolver);

    assertThatThrownBy(() -> drgEvaluator.evaluate(decisionA, variables))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Circular dependency");
  }
}
