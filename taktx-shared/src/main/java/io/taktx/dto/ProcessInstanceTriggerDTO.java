package io.taktx.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonFormat.Shape;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import com.fasterxml.jackson.databind.annotation.JsonTypeIdResolver;
import io.taktx.ProcessInstanceTriggerTypeIdResolver;
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
public abstract class ProcessInstanceTriggerDTO implements SchedulableMessageDTO {

  private UUID processInstanceKey;

  private VariablesDTO variables;

  protected ProcessInstanceTriggerDTO(UUID processInstanceKey, VariablesDTO variables) {
    this.processInstanceKey = processInstanceKey;
    this.variables = variables;
  }
}
