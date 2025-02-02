package com.flomaestro.takt.dto.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonFormat.Shape;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.flomaestro.takt.util.TaktUUIDDeserializer;
import com.flomaestro.takt.util.TaktUUIDSerializer;
import java.util.List;
import java.util.UUID;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@JsonFormat(shape = Shape.ARRAY)
@JsonInclude(Include.NON_NULL)
@Data
@NoArgsConstructor
@EqualsAndHashCode
@ToString
public class FlowNodeInstanceKeyDTO {
  @JsonSerialize(using = TaktUUIDSerializer.class)
  @JsonDeserialize(using = TaktUUIDDeserializer.class)
  private UUID processInstanceKey;

  private List<Long> flowNodeInstanceKeyPath;

  public FlowNodeInstanceKeyDTO(UUID processInstanceKey, List<Long> flowNodeInstanceKeyPath) {
    this.processInstanceKey = processInstanceKey;
    this.flowNodeInstanceKeyPath = flowNodeInstanceKeyPath;
  }
}
