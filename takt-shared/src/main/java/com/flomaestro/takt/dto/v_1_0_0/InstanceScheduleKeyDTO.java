package com.flomaestro.takt.dto.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonProperty;
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
  private UUID elementInstanceId;

  public InstanceScheduleKeyDTO(UUID processInstanceKey, UUID elementInstanceId) {
    this.processInstanceKey = processInstanceKey;
    this.elementInstanceId = elementInstanceId;
  }

  @Override
  public UUID getRecordKey() {
    return processInstanceKey;
  }
}
