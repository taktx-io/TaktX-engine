package nl.qunit.bpmnmeister.pd.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Builder
@Getter
@EqualsAndHashCode
public class ProcessDefinition {
  private final Definitions definitions;
  private final Integer version;

  @JsonCreator
  public ProcessDefinition(
      @JsonProperty("definitions") Definitions definitions, @JsonProperty("id") Integer version) {
    this.definitions = definitions;
    this.version = version;
  }

  @Override
  public String toString() {
    return "ProcessDefinition{" + "definitions=" + definitions + ", version=" + version + '}';
  }
}
