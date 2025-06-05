package io.taktx.dto;

import java.util.List;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CorrelationMessageSubscriptionDTO extends MessageEventDTO {

  private UUID processInstanceKey;

  private String correlationKey;

  private List<Long> elementInstanceIdPath;

  private String elementId;

  public CorrelationMessageSubscriptionDTO(
      UUID processInstanceKey,
      String correlationKey,
      List<Long> elementInstanceIdPath,
      String elementId,
      String messageName) {
    super(messageName);
    this.processInstanceKey = processInstanceKey;
    this.correlationKey = correlationKey;
    this.elementInstanceIdPath = elementInstanceIdPath;
    this.elementId = elementId;
  }
}
