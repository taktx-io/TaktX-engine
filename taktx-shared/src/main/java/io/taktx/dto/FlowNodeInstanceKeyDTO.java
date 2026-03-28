/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonFormat.Shape;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.quarkus.runtime.annotations.RegisterForReflection;
import io.taktx.util.TaktLongListDeserializer;
import io.taktx.util.TaktLongListSerializer;
import io.taktx.util.TaktUUIDDeserializer;
import io.taktx.util.TaktUUIDSerializer;
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
@RegisterForReflection
public class FlowNodeInstanceKeyDTO {
  @JsonSerialize(using = TaktUUIDSerializer.class)
  @JsonDeserialize(using = TaktUUIDDeserializer.class)
  private UUID processInstanceId;

  @JsonSerialize(using = TaktLongListSerializer.class)
  @JsonDeserialize(using = TaktLongListDeserializer.class)
  private List<Long> flowNodeInstanceKeyPath;

  public FlowNodeInstanceKeyDTO(UUID processInstanceId, List<Long> flowNodeInstanceKeyPath) {
    this.processInstanceId = processInstanceId;
    this.flowNodeInstanceKeyPath = flowNodeInstanceKeyPath;
  }
}
