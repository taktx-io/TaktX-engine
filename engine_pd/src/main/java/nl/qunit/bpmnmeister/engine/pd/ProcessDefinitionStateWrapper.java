package nl.qunit.bpmnmeister.engine.pd;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;

@Builder
@Getter
public class ProcessDefinitionStateWrapper {
  final ProcessDefinition processDefinition;
  final ProcessDefinitionStateEnum state;

  @JsonCreator
  public ProcessDefinitionStateWrapper(
      @JsonProperty("processDefinition") ProcessDefinition processDefinition,
      @JsonProperty("state") ProcessDefinitionStateEnum state) {
    this.processDefinition = processDefinition;
    this.state = state;
  }
}
