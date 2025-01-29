package com.flomaestro.takt.dto.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Set;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class ReceiveTaskDTO extends TaskDTO {

  @JsonProperty("r")
  private String messageRef;

  public ReceiveTaskDTO(
      String id,
      String parentId,
      Set<String> incoming,
      Set<String> outgoing,
      LoopCharacteristicsDTO loopCharacteristics,
      String messageRef,
      InputOutputMappingDTO ioMapping) {
    super(id, parentId, incoming, outgoing, loopCharacteristics, ioMapping);
    this.messageRef = messageRef;
  }
}
