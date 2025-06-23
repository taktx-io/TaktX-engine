/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.xml;

import io.taktx.bpmn.TEventDefinition;
import io.taktx.dto.EventDefinitionDTO;
import jakarta.xml.bind.JAXBElement;
import java.util.List;
import java.util.Set;

public interface EventDefinitionMapper {
  Set<EventDefinitionDTO> map(
      List<JAXBElement<? extends TEventDefinition>> eventDefinition, String parentId);
}
