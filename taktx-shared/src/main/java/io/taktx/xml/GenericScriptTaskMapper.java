/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.xml;

import io.taktx.bpmn.TScriptTask;
import io.taktx.dto.InputOutputMappingDTO;
import io.taktx.dto.LoopCharacteristicsDTO;
import io.taktx.dto.ScriptTaskDTO;

public class GenericScriptTaskMapper implements ScriptTaskMapper {

  @Override
  public ScriptTaskDTO map(
      TScriptTask serviceTask,
      String parentId,
      LoopCharacteristicsDTO loopCharacteristics,
      InputOutputMappingDTO ioMapping) {
    throw new UnsupportedOperationException(
        "GenericScriptTaskMapper does not support mapping for TScriptTask: " + serviceTask);
  }
}
