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
public class ProcessDefinition {

  public static final ProcessDefinition NONE =
      new ProcessDefinition(Definitions.NONE, -1, ProcessDefinitionStateEnum.INACTIVE);

  private final Definitions definitions;
  private final Integer version;
  private final ProcessDefinitionStateEnum state;

  @JsonCreator
  public ProcessDefinition(
      @Nonnull @JsonProperty("definitions") Definitions definitions,
      @Nonnull @JsonProperty("id") Integer version,
      @Nonnull @JsonProperty("state") ProcessDefinitionStateEnum state) {
    this.definitions = definitions;
    this.version = version;
    this.state = state;
  }

}
