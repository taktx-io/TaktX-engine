package nl.qunit.bpmnmeister.pd.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;


@Getter
@EqualsAndHashCode
public class DefinitionsKey {

  public static final DefinitionsKey NONE = new DefinitionsKey("", "");

  private final String processDefinitionId;
  private final String hash;

  @JsonCreator
  public DefinitionsKey(
      @JsonProperty("processDefinitionId") String processDefinitionId,
      @JsonProperty("hash") String hash) {
    this.processDefinitionId = processDefinitionId;
    this.hash = hash;
  }
}
