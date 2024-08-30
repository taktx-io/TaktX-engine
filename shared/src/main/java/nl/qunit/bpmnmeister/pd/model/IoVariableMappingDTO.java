package nl.qunit.bpmnmeister.pd.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode
public class IoVariableMappingDTO {

  private final String source;
  private final String target;

  @JsonCreator
  public IoVariableMappingDTO(
      @JsonProperty("source") String source, @JsonProperty("target") String target) {
    this.source = source;
    this.target = target;
  }
}
