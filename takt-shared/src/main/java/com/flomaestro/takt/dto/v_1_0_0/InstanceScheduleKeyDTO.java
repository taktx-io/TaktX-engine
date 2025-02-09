package com.flomaestro.takt.dto.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@ToString
@Getter
@Setter
@EqualsAndHashCode(callSuper = true)
@NoArgsConstructor
public class InstanceScheduleKeyDTO extends ScheduleKeyDTO {

  @JsonProperty("p")
  private UUID processInstanceKey;

  @JsonProperty("e")
  private List<Long> elementInstanceIdPath;

  public InstanceScheduleKeyDTO(UUID processInstanceKey, List<Long> elementInstanceIdPath, TimeBucket timeBucket) {
    super(timeBucket);
    this.processInstanceKey = processInstanceKey;
    this.elementInstanceIdPath = elementInstanceIdPath;
  }
}
