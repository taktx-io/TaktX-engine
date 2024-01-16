package nl.qunit.bpmnmeister.engine.pd;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

@Builder
@Getter
public class ProcessActivation {
  ProcessDefinitionStateEnum newState;

  @JsonCreator
  ProcessActivation(@JsonProperty("newState") ProcessDefinitionStateEnum newState) {
    this.newState = newState;
  }
}
