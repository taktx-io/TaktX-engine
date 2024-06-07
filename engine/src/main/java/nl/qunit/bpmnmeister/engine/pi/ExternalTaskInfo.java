package nl.qunit.bpmnmeister.engine.pi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import nl.qunit.bpmnmeister.pi.Variables;

@Getter
public class ExternalTaskInfo {

  private final String externalTaskId;
  private final Variables variables;

  @JsonCreator
  public ExternalTaskInfo(
      @JsonProperty("externalTaskId") String externalTaskId,
      @JsonProperty("variables") Variables variables) {
    this.externalTaskId = externalTaskId;
    this.variables = variables;
  }
}
