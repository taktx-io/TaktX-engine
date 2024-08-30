package nl.qunit.bpmnmeister.pd.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

@EqualsAndHashCode
@Getter
@ToString
public class ProcessDefinition2 {

  public static final ProcessDefinition2 NONE =
      new ProcessDefinition2(DefinitionsDTO.NONE, -1, ProcessDefinitionStateEnum.INACTIVE);

  private final DefinitionsDTO definitions;
  private final Integer version;
  private final ProcessDefinitionStateEnum state;

  @JsonCreator
  public ProcessDefinition2(
      @Nonnull @JsonProperty("definitions") DefinitionsDTO definitions,
      @Nonnull @JsonProperty("id") Integer version,
      @Nonnull @JsonProperty("state") ProcessDefinitionStateEnum state) {
    this.definitions = definitions;
    this.version = version;
    this.state = state;
  }
}
