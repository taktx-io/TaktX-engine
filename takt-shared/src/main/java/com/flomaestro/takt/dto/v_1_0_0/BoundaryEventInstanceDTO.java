package com.flomaestro.takt.dto.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import java.util.Set;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
@ToString(callSuper = true)
public class BoundaryEventInstanceDTO extends CatchEventInstanceDTO {

  @JsonProperty("a")
  private long attachedInstanceId;

  public BoundaryEventInstanceDTO(
      long elementInstanceId,
      String elementId,
      int passedCnt,
      long attachedInstanceId,
      Map<MessageEventKeyDTO, Set<String>> messageEventKeys,
      CatchEventStateEnum state,
      Set<ScheduleKeyDTO> scheduledKeys) {
    super(elementInstanceId, elementId, passedCnt, state, scheduledKeys, messageEventKeys);
    this.attachedInstanceId = attachedInstanceId;
  }
}
