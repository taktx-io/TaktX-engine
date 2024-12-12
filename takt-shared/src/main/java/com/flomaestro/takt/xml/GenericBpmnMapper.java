package com.flomaestro.takt.xml;

import com.flomaestro.bpmn.TDefinitions;
import com.flomaestro.bpmn.TError;
import com.flomaestro.bpmn.TEscalation;
import com.flomaestro.bpmn.TMessage;
import com.flomaestro.bpmn.TProcess;
import com.flomaestro.bpmn.TRootElement;
import com.flomaestro.takt.dto.v_1_0_0.DefinitionsKey;
import com.flomaestro.takt.dto.v_1_0_0.ErrorDTO;
import com.flomaestro.takt.dto.v_1_0_0.EscalationDTO;
import com.flomaestro.takt.dto.v_1_0_0.MessageDTO;
import com.flomaestro.takt.dto.v_1_0_0.ParsedDefinitionsDTO;
import com.flomaestro.takt.dto.v_1_0_0.ParsedDefinitionsDTO.ParsedDefinitionsDTOBuilder;
import com.flomaestro.takt.dto.v_1_0_0.ProcessDTO;
import jakarta.xml.bind.JAXBElement;
import java.util.HashMap;

public class GenericBpmnMapper implements BpmnMapper {

  private final BpmnMapperFactory bpmnMapperFactory;

  public GenericBpmnMapper(BpmnMapperFactory bpmnMapperFactory) {
    this.bpmnMapperFactory = bpmnMapperFactory;
  }

  public ParsedDefinitionsDTO map(TDefinitions definitions, String hash) {

    ParsedDefinitionsDTOBuilder builder = ParsedDefinitionsDTO.builder();
    HashMap<String, MessageDTO> messages = new HashMap<>();
    HashMap<String, EscalationDTO> escalations = new HashMap<>();
    HashMap<String, ErrorDTO> errors = new HashMap<>();
    builder.messages(messages);
    builder.escalations(escalations);
    builder.errors(errors);
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
      }
    }

    return builder.build();
  }
}
