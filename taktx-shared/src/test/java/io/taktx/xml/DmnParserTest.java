/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.xml;

import static org.assertj.core.api.Assertions.assertThat;

import io.taktx.dto.DmnCollectOperator;
import io.taktx.dto.DmnDecisionDTO;
import io.taktx.dto.DmnHitPolicy;
import io.taktx.dto.ParsedDmnDefinitionsDTO;
import org.junit.jupiter.api.Test;

class DmnParserTest {

  private static final String SIMPLE_DMN =
      """
      <?xml version="1.0" encoding="UTF-8"?>
      <definitions xmlns="https://www.omg.org/spec/DMN/20191111/MODEL/"
                   id="dish" name="Dish" namespace="http://camunda.org/schema/1.0/dmn">
        <decision id="dish-decision" name="Dish Decision">
          <decisionTable id="decisionTable" hitPolicy="UNIQUE">
            <input id="input1" label="Season">
              <inputExpression id="inputExpression1" typeRef="string">
                <text>season</text>
              </inputExpression>
            </input>
            <output id="output1" label="Dish" name="dish" typeRef="string"/>
            <rule id="rule1">
              <inputEntry id="inputEntry1"><text>"Fall"</text></inputEntry>
              <outputEntry id="outputEntry1"><text>"Roastbeef"</text></outputEntry>
            </rule>
            <rule id="rule2">
              <inputEntry id="inputEntry2"><text>"Winter"</text></inputEntry>
              <outputEntry id="outputEntry2"><text>"Roastbeef"</text></outputEntry>
            </rule>
          </decisionTable>
        </decision>
      </definitions>
      """;

  private static final String LITERAL_DMN =
      """
      <?xml version="1.0" encoding="UTF-8"?>
      <definitions xmlns="https://www.omg.org/spec/DMN/20191111/MODEL/"
                   id="discount" name="Discount" namespace="http://camunda.org/schema/1.0/dmn">
        <decision id="discount-decision" name="Discount">
          <literalExpression id="le1" typeRef="double">
            <text>=0.1</text>
          </literalExpression>
        </decision>
      </definitions>
      """;

  private static final String DRG_DMN =
      """
      <?xml version="1.0" encoding="UTF-8"?>
      <definitions xmlns="https://www.omg.org/spec/DMN/20191111/MODEL/"
                   id="drg" name="DRG" namespace="http://camunda.org/schema/1.0/dmn">
        <decision id="categoryDecision" name="Category Decision">
          <decisionTable id="catTable" hitPolicy="UNIQUE">
            <input id="ci1"><inputExpression id="ce1"><text>loyaltyPoints</text></inputExpression></input>
            <output id="co1" name="category" typeRef="string"/>
            <rule id="cr1"><inputEntry id="cie1"><text>&gt;= 1000</text></inputEntry><outputEntry id="coe1"><text>"Premium"</text></outputEntry></rule>
          </decisionTable>
        </decision>
        <decision id="discountDecision" name="Discount Decision">
          <informationRequirement id="ir1">
            <requiredDecision href="#categoryDecision"/>
          </informationRequirement>
          <decisionTable id="discTable" hitPolicy="UNIQUE">
            <input id="di1"><inputExpression id="de1"><text>categoryDecision</text></inputExpression></input>
            <output id="do1" name="discount" typeRef="double"/>
            <rule id="dr1"><inputEntry id="die1"><text>"Premium"</text></inputEntry><outputEntry id="doe1"><text>0.2</text></outputEntry></rule>
          </decisionTable>
        </decision>
      </definitions>
      """;

  @Test
  void parse_simpleDecisionTable() {
    ParsedDmnDefinitionsDTO result = DmnParser.parse(SIMPLE_DMN);

    assertThat(result.getDefinitionsKey().getDmnDefinitionId()).isEqualTo("dish");
    assertThat(result.getDefinitionsKey().getHash()).isNotBlank();
    assertThat(result.getName()).isEqualTo("Dish");
    assertThat(result.getDecisions()).hasSize(1);

    DmnDecisionDTO decision = result.getDecisions().get(0);
    assertThat(decision.getId()).isEqualTo("dish-decision");
    assertThat(decision.getName()).isEqualTo("Dish Decision");
    assertThat(decision.getDecisionTable()).isNotNull();
    assertThat(decision.getLiteralExpression()).isNull();

    var table = decision.getDecisionTable();
    assertThat(table.getHitPolicy()).isEqualTo(DmnHitPolicy.UNIQUE);
    assertThat(table.getCollectOperator()).isEqualTo(DmnCollectOperator.NONE);
    assertThat(table.getInputs()).hasSize(1);
    assertThat(table.getOutputs()).hasSize(1);
    assertThat(table.getRules()).hasSize(2);

    assertThat(table.getInputs().get(0).getInputExpression()).isEqualTo("season");
    assertThat(table.getOutputs().get(0).getName()).isEqualTo("dish");
    assertThat(table.getRules().get(0).getInputEntries()).containsExactly("\"Fall\"");
    assertThat(table.getRules().get(0).getOutputEntries()).containsExactly("\"Roastbeef\"");
  }

  @Test
  void parse_sameXmlProducesSameHash() {
    ParsedDmnDefinitionsDTO r1 = DmnParser.parse(SIMPLE_DMN);
    ParsedDmnDefinitionsDTO r2 = DmnParser.parse(SIMPLE_DMN);
    assertThat(r1.getDefinitionsKey().getHash()).isEqualTo(r2.getDefinitionsKey().getHash());
  }

  @Test
  void parse_literalExpression() {
    ParsedDmnDefinitionsDTO result = DmnParser.parse(LITERAL_DMN);
    DmnDecisionDTO decision = result.getDecisions().get(0);
    assertThat(decision.getLiteralExpression()).isNotNull();
    assertThat(decision.getDecisionTable()).isNull();
    assertThat(decision.getLiteralExpression().getExpression()).isEqualTo("=0.1");
  }

  @Test
  void parse_drgInformationRequirement_populatesRequiredDecisionIds() {
    ParsedDmnDefinitionsDTO result = DmnParser.parse(DRG_DMN);

    assertThat(result.getDecisions()).hasSize(2);

    DmnDecisionDTO category = result.getDecisions().get(0);
    assertThat(category.getId()).isEqualTo("categoryDecision");
    assertThat(category.getRequiredDecisionIds()).isNullOrEmpty();

    DmnDecisionDTO discount = result.getDecisions().get(1);
    assertThat(discount.getId()).isEqualTo("discountDecision");
    assertThat(discount.getRequiredDecisionIds()).containsExactly("categoryDecision");
  }
}
