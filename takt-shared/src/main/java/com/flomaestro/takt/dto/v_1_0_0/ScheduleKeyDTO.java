package com.flomaestro.takt.dto.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import com.fasterxml.jackson.databind.annotation.JsonTypeIdResolver;
import com.flomaestro.takt.ScheduleKeyTypeIdResolver;

@JsonTypeInfo(use = Id.CUSTOM, property = "c")
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonTypeIdResolver(ScheduleKeyTypeIdResolver.class)
public interface ScheduleKeyDTO {
  @JsonIgnore
  Object getRecordKey();
}
