package io.taktx.dto;

import java.util.List;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@ToString(callSuper = true)
@Getter
@Setter
@NoArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class InstanceScheduleKeyDTO extends ScheduleKeyDTO {

  private UUID processInstanceKey;

  private List<Long> elementInstanceIdPath;

  private String elementId;

  public InstanceScheduleKeyDTO(
      UUID processInstanceKey,
      List<Long> elementInstanceIdPath,
      String elementId,
      TimeBucket timeBucket) {
    super(timeBucket);
    this.processInstanceKey = processInstanceKey;
    this.elementInstanceIdPath = elementInstanceIdPath;
    this.elementId = elementId;
  }
}
