/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */

package io.taktx.engine.feel;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import io.taktx.engine.pi.model.VariableScope;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.HashMap;
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
public class FeelExpressionHandlerImpl implements FeelExpressionHandler {

  private static final BuiltinFunctions BUILTIN_FUNCTIONS =
      new BuiltinFunctions(SystemClock$.MODULE$, ValueMapper.defaultValueMapper());
  private final FeelEngineProvider feelEngineProvider;
  private final ObjectMapper objectMapper;
  private final Map<String, ParsedExpression> parsedExpressionCache = new HashMap<>();

  public FeelExpressionHandlerImpl(
      FeelEngineProvider feelEngineProvider, ObjectMapper objectMapper) {
    this.feelEngineProvider = feelEngineProvider;
    this.objectMapper = objectMapper;
  }

  public JsonNode processFeelExpression(String expression, VariableScope variables) {
    JsonNode resultNode;
    expression = expression == null ? "" : expression.trim();
    if (expression.startsWith("=")) {
      FeelEngineApi feelEngineApi = feelEngineProvider.getFeelEngineApi();
      ParsedExpression parsedExpression = getParsedExpression(feelEngineApi, expression);
      EvaluationResult evaluationResult =
          feelEngineApi.evaluate(parsedExpression, createContext(variables));
      if (evaluationResult.isSuccess()) {
        Object expressionResult =
            ((SuccessfulEvaluationResult) evaluationResult).productIterator().next();
        resultNode = objectMapper.valueToTree(expressionResult);
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

  private Context createContext(VariableScope variables) {
    return new Context() {
      @Override
      public VariableProvider variableProvider() {
        return new VariableProvider() {
          @Override
          public Option<Object> getVariable(String name) {
            try {
              return Option.apply(objectMapper.treeToValue(variables.get(name), Object.class));
            } catch (JsonProcessingException e) {
              throw new IllegalStateException(e);
            }
          }

          @Override
          public Iterable<String> keys() {
            log.error("THe keys method is called although not all variables might be available");
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

  private ParsedExpression getParsedExpression(FeelEngineApi feelEngineApi, String expression) {
    ParsedExpression parsedExpression = parsedExpressionCache.get(expression);
    if (parsedExpression == null) {
      ParseResult parseResult = feelEngineApi.parseExpression(expression.substring(1));
      if (parseResult.isSuccess()) {
        parsedExpression = parseResult.parsedExpression();
        parsedExpressionCache.put(expression, parsedExpression);
      }
    }
    return parsedExpression;
  }
}
