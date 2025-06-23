/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.xml;

import io.taktx.bpmn.TServiceTask;
import io.taktx.dto.InputOutputMappingDTO;
import io.taktx.dto.LoopCharacteristicsDTO;
import io.taktx.dto.ServiceTaskDTO;

public interface ServiceTaskMapper extends Mapper {
  String DEFAULT_RETRIES = "3";

  ServiceTaskDTO map(
      TServiceTask serviceTask,
      String parentId,
      LoopCharacteristicsDTO loopCharacteristics,
      InputOutputMappingDTO ioMapping);
}
