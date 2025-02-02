package com.flomaestro.takt.dto.v_1_0_0;

import java.util.Map;
import java.util.Set;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@ToString(callSuper = true)
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class IntermediateCatchEventInstanceDTO extends CatchEventInstanceDTO {

  public IntermediateCatchEventInstanceDTO(
      CatchEventStateEnum state,
      long elementInstanceId,
      String elementId,
      int passedCnt,
      Set<ScheduleKeyDTO> scheduledKeys,
      Map<MessageEventKeyDTO, Set<String>> messageEventKeys) {
    super(elementInstanceId, elementId, passedCnt, state, scheduledKeys, messageEventKeys);
  }
}
