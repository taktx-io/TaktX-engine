/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */

package io.taktx.engine.feel;

import static org.assertj.core.api.Assertions.assertThat;

import io.taktx.engine.pi.model.VariableScope;
import io.taktx.proto.VariableValue;
import io.taktx.variables.Variables;
import java.util.List;
import org.junit.jupiter.api.Test;

class FeelExpressionHandlerImplTest {

  private static Object asJavaObject(VariableValue value) {
    return Variables.toJavaObject(value);
  }

  @Test
  void testNoFeel() {
    FeelExpressionHandlerImpl expressionHandler =
        new FeelExpressionHandlerImpl(new FeelEngineProvider());
    Object result =
        asJavaObject(expressionHandler.processFeelExpression("test", VariableScope.empty(null, null)));
    assertThat(result).isEqualTo("test");
  }

  @Test
  void testSimpleFeel() {
    FeelExpressionHandlerImpl expressionHandler =
        new FeelExpressionHandlerImpl(new FeelEngineProvider());
    VariableValue value =
        expressionHandler.processFeelExpression("=\"test\"", VariableScope.empty(null, null));

    assertThat(value.getKindCase()).isEqualTo(VariableValue.KindCase.STRING_VALUE);
    assertThat(value.getStringValue()).isEqualTo("test");
  }

  @Test
  void testArithmeticFeelReturnsLongVariableValue() {
    FeelExpressionHandlerImpl expressionHandler =
        new FeelExpressionHandlerImpl(new FeelEngineProvider());
    VariableScope vars = VariableScope.empty(null, null);
    vars.put("amount", Variables.of(90L));

    VariableValue value = expressionHandler.processFeelExpression("= amount + 10", vars);

    assertThat(value.getKindCase()).isEqualTo(VariableValue.KindCase.LONG_VALUE);
    assertThat(value.getLongValue()).isEqualTo(100L);
  }

  @Test
  void testMapFeelReturnsMapVariableValue() {
    FeelExpressionHandlerImpl expressionHandler =
        new FeelExpressionHandlerImpl(new FeelEngineProvider());

    VariableValue value =
        expressionHandler.processFeelExpression(
            "={ status: \"ok\", amount: 100 }", VariableScope.empty(null, null));

    assertThat(value.getKindCase()).isEqualTo(VariableValue.KindCase.MAP_VALUE);
    assertThat(Variables.toJavaObject(value))
        .isEqualTo(java.util.Map.of("status", "ok", "amount", 100L));
  }

  @Test
  void testReferToVariableFeel() {
    FeelExpressionHandlerImpl expressionHandler =
        new FeelExpressionHandlerImpl(new FeelEngineProvider());
    VariableScope vars = VariableScope.empty(null, null);
    vars.put("var", Variables.of("test"));
    Object result = asJavaObject(expressionHandler.processFeelExpression("=var", vars));
    assertThat(result).isEqualTo("test");
  }

  @Test
  void testReferToVariableNotExisting() {
    FeelExpressionHandlerImpl expressionHandler =
        new FeelExpressionHandlerImpl(new FeelEngineProvider());
    VariableScope vars = VariableScope.empty(null, null);
    vars.put("var", Variables.of("test"));
    Object result = asJavaObject(expressionHandler.processFeelExpression("=var2", vars));
    assertThat(result).isNull();
  }

  @Test
  void testCreateRange() {
    FeelExpressionHandlerImpl expressionHandler =
        new FeelExpressionHandlerImpl(new FeelEngineProvider());
    VariableScope vars = VariableScope.empty(null, null);

    Object result = asJavaObject(expressionHandler.processFeelExpression("=for i in 1..100 return i", vars));
    assertThat(result).isInstanceOf(List.class);
    assertThat((List<?>) result).hasSize(100);
  }

  @Test
  void testReferToElementInArray() {
    FeelExpressionHandlerImpl expressionHandler =
        new FeelExpressionHandlerImpl(new FeelEngineProvider());
    VariableScope vars = VariableScope.empty(null, null);
    vars.put("myArray", Variables.of(List.of("test1", "test2", "test3")));

    Object result = asJavaObject(expressionHandler.processFeelExpression("=myArray[2]", vars));
    assertThat(result).isEqualTo("test2");
  }
}
