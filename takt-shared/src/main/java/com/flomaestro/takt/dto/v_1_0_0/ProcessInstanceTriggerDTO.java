package com.flomaestro.takt.dto.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonFormat.Shape;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import com.fasterxml.jackson.databind.annotation.JsonTypeIdResolver;
import com.flomaestro.takt.ProcessInstanceTriggerTypeIdResolver;
import java.util.List;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@JsonTypeInfo(use = Id.CUSTOM, property = "c")
@JsonTypeIdResolver(ProcessInstanceTriggerTypeIdResolver.class)
@JsonFormat(shape = Shape.ARRAY)
@JsonInclude(JsonInclude.Include.NON_NULL)
@ToString
@Getter
@Setter
@EqualsAndHashCode
@NoArgsConstructor
public abstract class ProcessInstanceTriggerDTO
    implements SchedulableMessageDTO {

  @JsonProperty("pik")
  private UUID processInstanceKey;

  @JsonProperty("eip")
  private List<String> elementIdPath;

  @JsonProperty("vars")
  private VariablesDTO variables;

  protected ProcessInstanceTriggerDTO(
      UUID processInstanceKey, List<String> elementIdPath, VariablesDTO variables) {
    this.processInstanceKey = processInstanceKey;
    this.elementIdPath = elementIdPath;
    this.variables = variables;
  }
}
