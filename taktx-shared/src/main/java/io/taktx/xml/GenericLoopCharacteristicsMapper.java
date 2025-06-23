/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.xml;

import io.taktx.bpmn.TLoopCharacteristics;
import io.taktx.bpmn.TMultiInstanceLoopCharacteristics;
import io.taktx.dto.LoopCharacteristicsDTO;
import jakarta.xml.bind.JAXBElement;

public class GenericLoopCharacteristicsMapper implements LoopCharacteristicsMapper {

  public LoopCharacteristicsDTO map(
      JAXBElement<? extends TLoopCharacteristics> loopCharacteristics) {
    if (loopCharacteristics != null) {
      TLoopCharacteristics tLoopCharacteristics = loopCharacteristics.getValue();
      if (tLoopCharacteristics instanceof TMultiInstanceLoopCharacteristics tLoopCharacteristics1) {
        return new LoopCharacteristicsDTO(
            tLoopCharacteristics1.isIsSequential(),
            tLoopCharacteristics1.getLoopDataInputRef().toString(),
            tLoopCharacteristics1.getInputDataItem().getName(),
            tLoopCharacteristics1.getLoopDataOutputRef().toString(),
            tLoopCharacteristics1.getOutputDataItem().getName());
      }
    }
    return LoopCharacteristicsDTO.NONE;
  }
}
