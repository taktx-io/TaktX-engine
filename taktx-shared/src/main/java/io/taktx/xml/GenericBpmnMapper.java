/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.xml;

import io.taktx.bpmn.TDefinitions;
import io.taktx.bpmn.TError;
import io.taktx.bpmn.TEscalation;
import io.taktx.bpmn.TMessage;
import io.taktx.bpmn.TProcess;
import io.taktx.bpmn.TRootElement;
import io.taktx.bpmn.TSignal;
import io.taktx.dto.DefinitionsKey;
import io.taktx.dto.ErrorDTO;
import io.taktx.dto.EscalationDTO;
import io.taktx.dto.MessageDTO;
import io.taktx.dto.ParsedDefinitionsDTO;
import io.taktx.dto.ProcessDTO;
import io.taktx.dto.SigDTO;
import jakarta.xml.bind.JAXBElement;
import java.util.HashMap;

public class GenericBpmnMapper implements BpmnMapper {

  private final BpmnMapperFactory bpmnMapperFactory;

  public GenericBpmnMapper(BpmnMapperFactory bpmnMapperFactory) {
    this.bpmnMapperFactory = bpmnMapperFactory;
  }

  public ParsedDefinitionsDTO map(TDefinitions definitions, String hash) {

    ParsedDefinitionsDTO.ParsedDefinitionsDTOBuilder builder = ParsedDefinitionsDTO.builder();
    HashMap<String, MessageDTO> messages = new HashMap<>();
    HashMap<String, EscalationDTO> escalations = new HashMap<>();
    HashMap<String, ErrorDTO> errors = new HashMap<>();
    HashMap<String, SigDTO> signals = new HashMap<>();
    builder.messages(messages);
    builder.escalations(escalations);
    builder.errors(errors);
    builder.signals(signals);

    for (JAXBElement<? extends TRootElement> jaxbElement : definitions.getRootElement()) {
      TRootElement tRootElement = jaxbElement.getValue();
      if (tRootElement instanceof TProcess tProcess) {
        builder.definitionsKey(new DefinitionsKey(tRootElement.getId(), hash));
        ProcessDTO rootElement = bpmnMapperFactory.createRootElementMapper().map(tProcess);
        builder.rootProcess(rootElement);
      } else if (tRootElement instanceof TMessage tMessage) {
        MessageMapper messageMapper = bpmnMapperFactory.createMessageMapper();
        messages.put(tMessage.getId(), messageMapper.map(tMessage));
      } else if (tRootElement instanceof TEscalation tEscalation) {
        EscalationMapper escalationMapper = bpmnMapperFactory.createEscalationMapper();
        escalations.put(tEscalation.getId(), escalationMapper.map(tEscalation));
      } else if (tRootElement instanceof TError tError) {
        ErrorMapper errorMapper = bpmnMapperFactory.createErrorMapper();
        errors.put(tError.getId(), errorMapper.map(tError));
      } else if (tRootElement instanceof TSignal tSignal) {
        SignalMapper signalMapper = bpmnMapperFactory.createSignalMapper();
        signals.put(tSignal.getId(), signalMapper.map(tSignal));
      }
    }

    return builder.build();
  }
}
