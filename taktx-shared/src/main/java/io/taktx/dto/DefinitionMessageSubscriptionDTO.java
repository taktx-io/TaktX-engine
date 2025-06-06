package io.taktx.dto;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class DefinitionMessageSubscriptionDTO extends MessageEventDTO {
  private ProcessDefinitionKey processDefinitionKey;

  private String elementId;

  public DefinitionMessageSubscriptionDTO(
      ProcessDefinitionKey processDefinitionKey, String elementId, String messageName) {
    super(messageName);
    this.processDefinitionKey = processDefinitionKey;
    this.elementId = elementId;
  }
}
