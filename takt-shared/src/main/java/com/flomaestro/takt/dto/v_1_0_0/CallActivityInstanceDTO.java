package com.flomaestro.takt.dto.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@ToString
@EqualsAndHashCode(callSuper = true)
@Data
@NoArgsConstructor
public class CallActivityInstanceDTO extends ActivityInstanceDTO {

  @JsonProperty("cpi")
  private UUID childProcessInstanceId;

}
