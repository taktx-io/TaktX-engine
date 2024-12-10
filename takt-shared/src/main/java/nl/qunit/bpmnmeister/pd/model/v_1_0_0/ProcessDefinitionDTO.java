package nl.qunit.bpmnmeister.pd.model.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@EqualsAndHashCode
@Getter
@ToString
public class ProcessDefinitionDTO {

  private final ParsedDefinitionsDTO definitions;
  private final Integer version;
  private final ProcessDefinitionStateEnum state;

  @JsonCreator
  public ProcessDefinitionDTO(
      @Nonnull @JsonProperty("definitions") ParsedDefinitionsDTO definitions,
      @Nonnull @JsonProperty("id") Integer version,
      @Nonnull @JsonProperty("state") ProcessDefinitionStateEnum state) {
    this.definitions = definitions;
    this.version = version;
    this.state = state;
  }
}
