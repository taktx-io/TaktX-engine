package nl.qunit.bpmnmeister.pi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.ToString;

@ToString
public class TerminateTrigger extends ProcessInstanceTrigger {
  @JsonCreator
  public TerminateTrigger(
      @JsonProperty("processInstanceKey") ProcessInstanceKey processInstanceKey,
      @JsonProperty("elementId") String elementId) {
    super(processInstanceKey, elementId, Variables.EMPTY);
  }
}
