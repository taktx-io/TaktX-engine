package com.flomaestro.takt.dto.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import com.fasterxml.jackson.databind.annotation.JsonTypeIdResolver;
import com.flomaestro.takt.ProcessInstanceTriggerTypeIdResolver;

@JsonTypeInfo(use = Id.CUSTOM, property = "c")
@JsonTypeIdResolver(ProcessInstanceTriggerTypeIdResolver.class)
public interface SchedulableMessageDTO {}
