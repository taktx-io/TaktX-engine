/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.xml;

import io.taktx.bpmn.TArtifact;
import io.taktx.bpmn.TAssociation;
import io.taktx.bpmn.TFlowElement;
import io.taktx.bpmn.TProcess;
import io.taktx.bpmn.TRootElement;
import io.taktx.bpmn.VersionTag;
import io.taktx.dto.BoundaryEventDTO;
import io.taktx.dto.CompensationEventDefinitionDTO;
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
      FlowElementsDTO elements = mapFlowElements(tProcess.getFlowElement());
      resolveCompensationAssociations(tProcess, elements);
      return new ProcessDTO(id, null, versionTagValue, elements);
    }
    return ProcessDTO.NONE;
  }

  private void resolveCompensationAssociations(TProcess tProcess, FlowElementsDTO elements) {
    if (tProcess.getArtifact() == null) {
      return;
    }
    for (JAXBElement<? extends TArtifact> jaxbArtifact : tProcess.getArtifact()) {
      TArtifact artifact = jaxbArtifact.getValue();
      if (!(artifact instanceof TAssociation association)) {
        continue;
      }
      String sourceId = association.getSourceRef().getLocalPart();
      String targetId = association.getTargetRef().getLocalPart();
      FlowElementDTO source = elements.getElements().get(sourceId);
      if (source instanceof BoundaryEventDTO boundaryEvent
          && boundaryEvent.getEventDefinitions().stream()
              .anyMatch(ed -> ed instanceof CompensationEventDefinitionDTO)) {
        elements
            .getElements()
            .put(
                sourceId,
                new BoundaryEventDTO(
                    boundaryEvent.getId(),
                    boundaryEvent.getParentId(),
                    boundaryEvent.getName(),
                    boundaryEvent.getIncoming(),
                    boundaryEvent.getOutgoing(),
                    boundaryEvent.getEventDefinitions(),
                    boundaryEvent.getAttachedToRef(),
                    boundaryEvent.isCancelActivity(),
                    boundaryEvent.getIoMapping(),
                    targetId));
      }
    }
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
