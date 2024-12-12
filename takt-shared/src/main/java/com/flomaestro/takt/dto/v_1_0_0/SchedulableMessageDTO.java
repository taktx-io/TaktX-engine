package com.flomaestro.takt.dto.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import com.fasterxml.jackson.databind.annotation.JsonTypeIdResolver;
import com.flomaestro.takt.CustomTypeIdResolver;
import java.util.UUID;

@JsonTypeInfo(use = Id.CUSTOM, property = "cls")
@JsonTypeIdResolver(CustomTypeIdResolver.class)
public interface SchedulableMessageDTO<T> {
  @JsonIgnore
  T getRecordKey(UUID processInstanceKey);
}
