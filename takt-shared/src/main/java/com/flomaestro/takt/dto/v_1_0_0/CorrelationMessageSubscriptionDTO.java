package com.flomaestro.takt.dto.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonProperty;
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

  @JsonProperty("pi")
  private UUID processInstanceKey;

  @JsonProperty("ck")
  private String correlationKey;

  @JsonProperty("eid")
  private List<String> elementIdPath;

  @JsonProperty("eiid")
  private List<UUID> elementInstanceIdPath;

  public CorrelationMessageSubscriptionDTO(
      UUID processInstanceKey,
      String correlationKey,
      List<String> elementIdPath,
      List<UUID> elementInstanceIdPath,
      String messageName) {
    super(messageName);
    this.processInstanceKey = processInstanceKey;
    this.correlationKey = correlationKey;
    this.elementIdPath = elementIdPath;
    this.elementInstanceIdPath = elementInstanceIdPath;
  }
}
