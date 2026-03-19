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
import io.taktx.InstanceUpdateTypeIdResolver;
import jakarta.annotation.Nullable;
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
@RegisterForReflection
public abstract class InstanceUpdateDTO {
  /**
   * Structured trust data for the command currently being processed when this update was emitted.
   */
  @Nullable private CommandTrustMetadataDTO currentTrustMetadata;

  /**
   * Structured provenance/trust data for the original external or worker command that started this
   * chain. This is intentionally about logical command provenance, not transport-level verification
   * of the outbound instance-update record itself.
   */
  @Nullable private CommandTrustMetadataDTO originTrustMetadata;

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
