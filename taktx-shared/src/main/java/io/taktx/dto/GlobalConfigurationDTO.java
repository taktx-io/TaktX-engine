/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 */
package io.taktx.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.quarkus.runtime.annotations.RegisterForReflection;
import java.util.List;
import lombok.*;

/** Cluster-wide engine configuration distributed via the taktx-configuration compacted topic. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode
@ToString
@RegisterForReflection
@JsonFormat(shape = JsonFormat.Shape.ARRAY)
public class GlobalConfigurationDTO {
  @Builder.Default private boolean signingEnabled = false;
  @Builder.Default private boolean rbacEnabled = false;
  private List<String> activeKeyIds;
}
