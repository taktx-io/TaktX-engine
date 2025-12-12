/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.pi.processor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.taktx.dto.ExecutionState;
import io.taktx.engine.feel.FeelExpressionHandler;
import io.taktx.engine.pd.model.Activity;
import io.taktx.engine.pd.model.LoopCharacteristics;
import io.taktx.engine.pi.model.ActivityInstance;
import io.taktx.engine.pi.model.Scope;
import io.taktx.engine.pi.model.VariableScope;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** TDD test for FEEL expression null handling in ActivityInstanceProcessor loop iterations. */
@ExtendWith(MockitoExtension.class)
class ActivityFeelNullHandlingTest {

  @Mock private FeelExpressionHandler feelExpressionHandler;
  @Mock private Scope scope;
  @Mock private ActivityInstance<Activity> activityInstance;
  @Mock private Activity activity;
  @Mock private LoopCharacteristics loopCharacteristics;
  @Mock private VariableScope variableScope;

  @BeforeEach
  void setUp() {
    when(scope.getVariableScope()).thenReturn(variableScope);
  }

  @Test
  void handleFinishedIteration_shouldHandleNullLoopCharacteristics() {
    // BUG: No null check for getLoopCharacteristics()
    // Expected: Should handle null gracefully
    // Actual (before fix): NullPointerException

    when(activityInstance.getState()).thenReturn(ExecutionState.COMPLETED);
    when(activityInstance.isIteration()).thenReturn(true);
    when(activityInstance.getFlowNode()).thenReturn(activity);
    when(activity.getLoopCharacteristics()).thenReturn(null); // Loop characteristics is null

    // Test via reflection since the method is private
    assertDoesNotThrow(
        () -> {
          java.lang.reflect.Method method =
              ActivityInstanceProcessor.class.getDeclaredMethod(
                  "handleFinishedIteration", ActivityInstance.class, Scope.class);
          method.setAccessible(true);

          // Create a simple test processor
          ActivityInstanceProcessor<
                  Activity, ActivityInstance<Activity>, io.taktx.dto.ContinueFlowElementTriggerDTO>
              processor = new TestActivityProcessor(feelExpressionHandler);

          method.invoke(processor, activityInstance, scope);
        });
  }

  @Test
  void handleFinishedIteration_shouldHandleNullFeelResult() {
    // BUG: No null check for processFeelExpression result
    // Expected: Should handle null gracefully, not set output element
    // Actual (before fix): NullPointerException or sets null

    when(activityInstance.getState()).thenReturn(ExecutionState.COMPLETED);
    when(activityInstance.isIteration()).thenReturn(true);
    when(activityInstance.getFlowNode()).thenReturn(activity);
    when(activity.getLoopCharacteristics()).thenReturn(loopCharacteristics);
    when(loopCharacteristics.getOutputElement()).thenReturn("missingVariable");

    // FEEL expression returns null
    when(feelExpressionHandler.processFeelExpression("missingVariable", variableScope))
        .thenReturn(null);

    // Test via reflection
    assertDoesNotThrow(
        () -> {
          java.lang.reflect.Method method =
              ActivityInstanceProcessor.class.getDeclaredMethod(
                  "handleFinishedIteration", ActivityInstance.class, Scope.class);
          method.setAccessible(true);

          ActivityInstanceProcessor<
                  Activity, ActivityInstance<Activity>, io.taktx.dto.ContinueFlowElementTriggerDTO>
              processor = new TestActivityProcessor(feelExpressionHandler);

          method.invoke(processor, activityInstance, scope);

          // Should not have called setOutputElement with null, or should handle gracefully
          verify(activityInstance, never()).setOutputElement(null);
        });
  }

  // Test implementation
  private static class TestActivityProcessor
      extends ActivityInstanceProcessor<
          Activity, ActivityInstance<Activity>, io.taktx.dto.ContinueFlowElementTriggerDTO> {

    TestActivityProcessor(FeelExpressionHandler feelExpressionHandler) {
      super(feelExpressionHandler, null, null, null);
    }

    @Override
    protected void processStartSpecificActivityInstance(
        io.taktx.engine.pi.ProcessInstanceProcessingContext processInstanceProcessingContext,
        Scope scope,
        ActivityInstance<Activity> flownodeInstance,
        String inputFlowId) {}

    @Override
    protected void processContinueSpecificActivityInstance(
        io.taktx.engine.pi.ProcessInstanceProcessingContext processInstanceProcessingContext,
        Scope scope,
        ActivityInstance<Activity> flowNodeInstance,
        io.taktx.dto.ContinueFlowElementTriggerDTO trigger) {}

    @Override
    protected void processAbortSpecificActivityInstance(
        io.taktx.engine.pi.ProcessInstanceProcessingContext processInstanceProcessingContext,
        Scope scope,
        ActivityInstance<Activity> instance) {}
  }
}
