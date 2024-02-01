package nl.qunit.bpmnmeister.pd.model;

import java.util.Map;
import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class Definitions {
  private String processDefinitionId;
  private Integer generation;
  private String hash;
  private Map<String, BaseElement> elements;

  @Override
  public String toString() {
    return "Definitions{"
        + "processDefinitionId='"
        + processDefinitionId
        + '\''
        + ", generation='"
        + generation
        + '\''
        + ", hash='"
        + hash
        + '\''
        + ", elements="
        + elements
        + '}';
  }
}
