/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonFormat.Shape;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import com.fasterxml.jackson.databind.annotation.JsonTypeIdResolver;
import io.quarkus.runtime.annotations.RegisterForReflection;
import io.taktx.ProcessInstanceTriggerTypeIdResolver;
import jakarta.annotation.Nullable;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@JsonTypeInfo(use = Id.CUSTOM, property = "c")
@JsonTypeIdResolver(ProcessInstanceTriggerTypeIdResolver.class)
@JsonFormat(shape = Shape.ARRAY)
@ToString
@Getter
@Setter
@EqualsAndHashCode
@NoArgsConstructor
@RegisterForReflection
public abstract class ProcessInstanceTriggerDTO implements SchedulableMessageDTO {

  private UUID processInstanceId;

  @Nullable private CommandTrustMetadataDTO currentTrustMetadata;

  @Nullable private CommandTrustMetadataDTO originTrustMetadata;

  protected ProcessInstanceTriggerDTO(UUID processInstanceId) {
    this.processInstanceId = processInstanceId;
  }

  @Deprecated
  @JsonIgnore
  public CommandTrustMetadataDTO getCommandTrustMetadata() {
    return currentTrustMetadata;
  }

  @Deprecated
  @JsonIgnore
  public void setCommandTrustMetadata(CommandTrustMetadataDTO commandTrustMetadata) {
    this.currentTrustMetadata = commandTrustMetadata;
  }
}
