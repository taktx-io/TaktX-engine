/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.engine.pi.testengine;

import static org.assertj.core.api.Assertions.assertThat;

import io.taktx.dto.UserTaskTriggerDTO;

public class UserTaskAssert {

  private final UserTaskTriggerDTO activeUserTaskTrigger;
  private final BpmnTestEngine bpmnTestEngine;

  public UserTaskAssert(UserTaskTriggerDTO activeUserTaskTrigger, BpmnTestEngine bpmnTestEngine) {
    this.activeUserTaskTrigger = activeUserTaskTrigger;
    this.bpmnTestEngine = bpmnTestEngine;
  }

  public UserTaskAssert hasAssignee(String assignee) {
    assertThat(activeUserTaskTrigger.getAssignmentDefinition().getAssignee())
        .as("Assignment definition should not be null")
        .isEqualTo(assignee);
    return this;
  }

  public UserTaskAssert hasCandidateGroups(String candidategroups) {
    assertThat(activeUserTaskTrigger.getAssignmentDefinition().getCandidateGroups())
        .as("Candidate groups should not be null")
        .isEqualTo(candidategroups);
    return this;
  }

  public UserTaskAssert hasCandidateUsers(String candidateUsers) {
    assertThat(activeUserTaskTrigger.getAssignmentDefinition().getCandidateUsers())
        .as("Candidate groups should not be null")
        .isEqualTo(candidateUsers);
    return this;
  }

  public UserTaskAssert hasPriority(String priority) {
    assertThat(activeUserTaskTrigger.getPriorityDefinition().getPriority())
        .as("Candidate groups should not be null")
        .isEqualTo(priority);
    return this;
  }

  public UserTaskAssert hasDueDate(String date) {
    assertThat(activeUserTaskTrigger.getTaskSchedule().getDueDate())
        .as("Candidate groups should not be null")
        .isEqualTo(date);
    return this;
  }

  public UserTaskAssert hasFollowupDate(String date) {
    assertThat(activeUserTaskTrigger.getTaskSchedule().getFollowUpDate())
        .as("Candidate groups should not be null")
        .isEqualTo(date);
    return this;
  }

  public BpmnTestEngine toProcessLevel() {
    return bpmnTestEngine;
  }
}
