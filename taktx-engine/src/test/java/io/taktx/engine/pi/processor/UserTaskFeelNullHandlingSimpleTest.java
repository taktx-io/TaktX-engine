/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */

package io.taktx.engine.pi.processor;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import io.taktx.bpmn.AssignmentDefinition;
import io.taktx.dto.AssignmentDefinitionDTO;
import io.taktx.engine.feel.FeelExpressionHandler;
import io.taktx.engine.pi.model.VariableScope;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * TDD test for FEEL expression null handling in UserTaskInstanceProcessor. Tests the specific
 * private methods that process FEEL expressions.
 */
@ExtendWith(MockitoExtension.class)
class UserTaskFeelNullHandlingSimpleTest {

  @Mock private FeelExpressionHandler feelExpressionHandler;
  @Mock private VariableScope variableScope;
  @Mock private AssignmentDefinition assignmentDefinition;

  @InjectMocks private UserTaskInstanceProcessor processor;

  @Test
  void getProcessedAssignmentDefinition_shouldHandleNullFeelResult() {
    // BUG: When FEEL expression returns null, .asText() throws NPE
    // Expected: Method should handle null and return DTO with null values
    // Actual (before fix): NullPointerException at .asText()

    when(assignmentDefinition.getAssignee()).thenReturn("missingVariable");
    when(assignmentDefinition.getCandidateGroups()).thenReturn("someGroup");
    when(assignmentDefinition.getCandidateUsers()).thenReturn("someUser");

    // Simulate FEEL expression returning null for first field
    when(feelExpressionHandler.processFeelExpression("missingVariable", variableScope))
        .thenReturn(null);
    when(feelExpressionHandler.processFeelExpression("someGroup", variableScope))
        .thenReturn(com.fasterxml.jackson.databind.node.TextNode.valueOf("group1"));
    when(feelExpressionHandler.processFeelExpression("someUser", variableScope))
        .thenReturn(com.fasterxml.jackson.databind.node.TextNode.valueOf("user1"));

    // This should NOT throw NPE
    // We test via reflection since the method is private
    try {
      java.lang.reflect.Method method =
          UserTaskInstanceProcessor.class.getDeclaredMethod(
              "getProcessedAssignmentDefinition", VariableScope.class, AssignmentDefinition.class);
      method.setAccessible(true);

      AssignmentDefinitionDTO result =
          (AssignmentDefinitionDTO) method.invoke(processor, variableScope, assignmentDefinition);

      // Should return DTO with null for missing variable
      assertNotNull(result);
      assertNull(result.getAssignee());
      assertEquals("group1", result.getCandidateGroups());
      assertEquals("user1", result.getCandidateUsers());

    } catch (Exception e) {
      if (e.getCause() instanceof NullPointerException) {
        fail("NullPointerException thrown when FEEL expression returns null - BUG CONFIRMED");
      }
      throw new RuntimeException(e);
    }
  }
}
