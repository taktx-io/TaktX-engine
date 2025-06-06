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
public class CancelDefinitionMessageSubscriptionDTO extends MessageEventDTO {
  public CancelDefinitionMessageSubscriptionDTO(String messageName) {
    super(messageName);
  }
}
