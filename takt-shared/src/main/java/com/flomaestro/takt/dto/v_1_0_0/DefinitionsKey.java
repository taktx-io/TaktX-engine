package com.flomaestro.takt.dto.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@EqualsAndHashCode
@ToString
@NoArgsConstructor
public class DefinitionsKey {

  public static final DefinitionsKey NONE = new DefinitionsKey("", "");

  @JsonProperty("p")
  private String processDefinitionId;

  @JsonProperty("h")
  private String hash;

  public DefinitionsKey(String processDefinitionId, String hash) {
    this.processDefinitionId = processDefinitionId;
    this.hash = hash;
  }
}
