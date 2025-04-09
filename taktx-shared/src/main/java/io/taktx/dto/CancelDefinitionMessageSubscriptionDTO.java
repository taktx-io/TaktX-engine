package io.taktx.dto;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CancelDefinitionMessageSubscriptionDTO extends MessageEventDTO {
  public CancelDefinitionMessageSubscriptionDTO(String messageName) {
    super(messageName);
  }
}
