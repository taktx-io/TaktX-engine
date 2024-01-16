package nl.qunit.bpmnmeister.pd.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

import java.util.Objects;

@Builder
@Getter
@EqualsAndHashCode
public class ProcessDefinitionKey {
  private final String processDefinitionId;
  private final String generation;
  private final Integer version;

  @JsonCreator
  public ProcessDefinitionKey(
      @JsonProperty("processDefinitionId") String processDefinitionId,
      @JsonProperty("generation") String generation,
      @JsonProperty("version") Integer version) {
    this.processDefinitionId = processDefinitionId;
    this.generation = generation;
    this.version = version;
  }

}
