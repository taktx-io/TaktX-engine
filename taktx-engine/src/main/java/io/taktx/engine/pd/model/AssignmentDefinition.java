package io.taktx.engine.pd.model;

import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
public class AssignmentDefinition {
  private String assignee;
  private String candidateGroups;
  private String candidateUsers;
}
