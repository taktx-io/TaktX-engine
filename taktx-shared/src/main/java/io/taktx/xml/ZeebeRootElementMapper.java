/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks All rights reserved.
 * This file is part of TaktX, licensed under the TaktX Business Source License v1.0.
 * Free use is permitted with up to 3 Kafka partitions per topic. See LICENSE file for details.
 * For commercial use or more partitions and features, contact [https://www.taktx.io/contact].
 */

package io.taktx.xml;

import io.taktx.bpmn.TFlowElement;
import io.taktx.bpmn.TProcess;
import io.taktx.bpmn.TRootElement;
import io.taktx.bpmn.VersionTag;
import io.taktx.dto.FlowElementDTO;
import io.taktx.dto.FlowElementsDTO;
import io.taktx.dto.ProcessDTO;
import jakarta.xml.bind.JAXBElement;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ZeebeRootElementMapper implements RootElementMapper {

  private final BpmnMapperFactory bpmnMapperFactory;

  public ZeebeRootElementMapper(BpmnMapperFactory bpmnMapperFactory) {
    this.bpmnMapperFactory = bpmnMapperFactory;
  }

  public ProcessDTO map(TRootElement tRootElement) {
    if (tRootElement instanceof TProcess tProcess) {
      String id = tProcess.getId();
      Optional<VersionTag> versionTag =
          ExtensionElementHelper.extractExtensionElement(
              tProcess.getExtensionElements(), VersionTag.class);
      String versionTagValue = versionTag.map(VersionTag::getValue).orElse(null);
      return new ProcessDTO(id, null, versionTagValue, mapFlowElements(tProcess.getFlowElement()));
    }
    return ProcessDTO.NONE;
  }

  private FlowElementsDTO mapFlowElements(
      List<JAXBElement<? extends TFlowElement>> jaxbFlowElementList) {
    Map<String, FlowElementDTO> flowElements = new HashMap<>();
    for (JAXBElement<? extends TFlowElement> jaxbFlowElement : jaxbFlowElementList) {
      TFlowElement tFlowElement = jaxbFlowElement.getValue();
      FlowElementDTO flowElement =
          bpmnMapperFactory.createFlowElementMapper().map(tFlowElement, null);
      flowElements.put(flowElement.getId(), flowElement);
    }
    return new FlowElementsDTO(flowElements);
  }
}
