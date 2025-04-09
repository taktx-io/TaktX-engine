package io.taktx.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class DefinitionMessageSubscriptionDTO extends MessageEventDTO {
  @JsonProperty("d")
  private ProcessDefinitionKey processDefinitionKey;

  @JsonProperty("e")
  private String elementId;

  public DefinitionMessageSubscriptionDTO(
      ProcessDefinitionKey processDefinitionKey, String elementId, String messageName) {
    super(messageName);
    this.processDefinitionKey = processDefinitionKey;
    this.elementId = elementId;
  }
}
