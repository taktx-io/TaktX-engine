package com.flomaestro.takt.dto.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonFormat.Shape;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.flomaestro.takt.util.TaktLongListDeserializer;
import com.flomaestro.takt.util.TaktLongListSerializer;
import com.flomaestro.takt.util.TaktUUIDDeserializer;
import com.flomaestro.takt.util.TaktUUIDSerializer;
import java.util.List;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@JsonFormat(shape = Shape.ARRAY)
@JsonInclude(Include.NON_NULL)
@NoArgsConstructor
@EqualsAndHashCode
@ToString
public class FlowNodeInstanceKeyDTO {
  @JsonSerialize(using = TaktUUIDSerializer.class)
  @JsonDeserialize(using = TaktUUIDDeserializer.class)
  @JsonProperty("processInstanceKey")
  private UUID processInstanceKey;

  @JsonProperty("flowNodeInstanceKeyPath")
  @JsonSerialize(using = TaktLongListSerializer.class)
  @JsonDeserialize(using = TaktLongListDeserializer.class)
  private List<Long> flowNodeInstanceKeyPath;

  public FlowNodeInstanceKeyDTO(UUID processInstanceKey, List<Long> flowNodeInstanceKeyPath) {
    this.processInstanceKey = processInstanceKey;
    this.flowNodeInstanceKeyPath = flowNodeInstanceKeyPath;
  }
}
