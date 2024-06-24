package nl.qunit.bpmnmeister.pi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import lombok.ToString;

@ToString
public class TerminateTrigger extends ProcessInstanceTrigger {
  @JsonCreator
  public TerminateTrigger(
      @Nonnull @JsonProperty("processInstanceKey") ProcessInstanceKey processInstanceKey,
      @Nonnull @JsonProperty("elementId") String elementId) {
    super(processInstanceKey, elementId, Variables.empty());
  }
}
