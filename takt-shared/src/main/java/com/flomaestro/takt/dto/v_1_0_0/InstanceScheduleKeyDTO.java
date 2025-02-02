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
@EqualsAndHashCode
@NoArgsConstructor
public class InstanceScheduleKeyDTO implements ScheduleKeyDTO {
  @JsonProperty("pik")
  private UUID processInstanceKey;

  @JsonProperty("eiid")
  private List<Long> elementInstanceIdPath;

  public InstanceScheduleKeyDTO(UUID processInstanceKey, List<Long> elementInstanceIdPath) {
    this.processInstanceKey = processInstanceKey;
    this.elementInstanceIdPath = elementInstanceIdPath;
  }

  @Override
  public UUID getRecordKey() {
    return processInstanceKey;
  }
}
