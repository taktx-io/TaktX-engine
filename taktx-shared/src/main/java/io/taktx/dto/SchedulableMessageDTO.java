package io.taktx.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonFormat.Shape;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import com.fasterxml.jackson.databind.annotation.JsonTypeIdResolver;
import io.taktx.ProcessInstanceTriggerTypeIdResolver;
import java.util.UUID;

@JsonTypeInfo(use = Id.CUSTOM, property = "c")
@JsonFormat(shape = Shape.ARRAY)
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonTypeIdResolver(ProcessInstanceTriggerTypeIdResolver.class)
public interface SchedulableMessageDTO {
  UUID getProcessInstanceKey();

  void setProcessInstanceKey(UUID processInstanceKey);
}
