package io.taktx.dto;

import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CancelCorrelationMessageSubscriptionDTO extends MessageEventDTO {

  private String correlationKey;

  public CancelCorrelationMessageSubscriptionDTO(String messageName, String correlationKey) {
    super(messageName);

    this.correlationKey = correlationKey;
  }
}
