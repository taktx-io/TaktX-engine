package nl.qunit.bpmnmeister.pi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@Getter
@EqualsAndHashCode
@ToString
public class ProcessInstanceKeyElementPair {

  private final ProcessInstanceKey processInstanceKey;
  private final String elementId;

  @JsonCreator
  public ProcessInstanceKeyElementPair(
      @JsonProperty("processInstanceKey") ProcessInstanceKey processInstanceKey,
      @JsonProperty("elementId") String elementId) {
    this.processInstanceKey = processInstanceKey;
    this.elementId = elementId;
  }
}
