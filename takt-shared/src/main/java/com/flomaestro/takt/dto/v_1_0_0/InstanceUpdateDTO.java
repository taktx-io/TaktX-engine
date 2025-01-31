package com.flomaestro.takt.dto.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonFormat.Shape;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import com.fasterxml.jackson.databind.annotation.JsonTypeIdResolver;
import com.flomaestro.takt.InstanceUpdateTypeIdResolver;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@JsonTypeInfo(use = Id.CUSTOM, property = "c")
@JsonTypeIdResolver(InstanceUpdateTypeIdResolver.class)
@JsonFormat(shape = Shape.ARRAY)
@Setter
@Getter
@NoArgsConstructor
@EqualsAndHashCode
@ToString
public abstract class InstanceUpdateDTO {

  @JsonProperty("p")
  private UUID processInstanceKey;

  protected InstanceUpdateDTO(UUID processInstanceKey) {
    this.processInstanceKey = processInstanceKey;
  }
}
