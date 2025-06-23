/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.xml;

import io.taktx.bpmn.LoopCharacteristics;
import io.taktx.bpmn.TLoopCharacteristics;
import io.taktx.bpmn.TMultiInstanceLoopCharacteristics;
import io.taktx.dto.LoopCharacteristicsDTO;
import jakarta.xml.bind.JAXBElement;
import java.util.Optional;

public class ZeebeLoopCharacteristicsMapper implements LoopCharacteristicsMapper {

  public LoopCharacteristicsDTO map(
      JAXBElement<? extends TLoopCharacteristics> loopCharacteristics) {
    if (loopCharacteristics != null) {
      TLoopCharacteristics tLoopCharacteristics = loopCharacteristics.getValue();
      if (tLoopCharacteristics
          instanceof TMultiInstanceLoopCharacteristics multiInstanceLoopCharacteristics) {
        Optional<LoopCharacteristics> optLoop =
            ExtensionElementHelper.extractExtensionElement(
                tLoopCharacteristics.getExtensionElements(), LoopCharacteristics.class);
        if (optLoop.isPresent()) {
          LoopCharacteristics zeebeLoop = optLoop.get();
          return new LoopCharacteristicsDTO(
              multiInstanceLoopCharacteristics.isIsSequential(),
              zeebeLoop.getInputCollection(),
              zeebeLoop.getInputElement(),
              zeebeLoop.getOutputCollection(),
              zeebeLoop.getOutputElement());
        }
      }
    }
    return LoopCharacteristicsDTO.NONE;
  }
}
