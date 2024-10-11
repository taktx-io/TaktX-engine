package nl.qunit.bpmnmeister.pd.xml;

import jakarta.xml.bind.JAXBElement;
import java.util.HashMap;
import nl.qunit.bpmnmeister.bpmn.TDefinitions;
import nl.qunit.bpmnmeister.bpmn.TEscalation;
import nl.qunit.bpmnmeister.bpmn.TMessage;
import nl.qunit.bpmnmeister.bpmn.TProcess;
import nl.qunit.bpmnmeister.bpmn.TRootElement;
import nl.qunit.bpmnmeister.pd.model.DefinitionsDTO;
import nl.qunit.bpmnmeister.pd.model.DefinitionsDTO.DefinitionsDTOBuilder;
import nl.qunit.bpmnmeister.pd.model.DefinitionsKey;
import nl.qunit.bpmnmeister.pd.model.EscalationDTO;
import nl.qunit.bpmnmeister.pd.model.MessageDTO;
import nl.qunit.bpmnmeister.pd.model.Process;

public class GenericBpmnMapper implements BpmnMapper {

  private final BpmnMapperFactory bpmnMapperFactory;

  public GenericBpmnMapper(BpmnMapperFactory bpmnMapperFactory) {
    this.bpmnMapperFactory = bpmnMapperFactory;
  }

  public DefinitionsDTO map(TDefinitions definitions, String hash) {

    DefinitionsDTOBuilder builder = DefinitionsDTO.builder();
    HashMap<String, MessageDTO> messages = new HashMap<>();
    HashMap<String, EscalationDTO> escalations = new HashMap<>();
    builder.messages(messages);
    builder.escalations(escalations);
    for (JAXBElement<? extends TRootElement> jaxbElement : definitions.getRootElement()) {
      TRootElement tRootElement = jaxbElement.getValue();
      builder.definitionsKey(new DefinitionsKey(tRootElement.getId(), hash));
      if (tRootElement instanceof TProcess tProcess) {
        Process rootElement = bpmnMapperFactory.createRootElementMapper().map(tProcess);
        builder.rootProcess(rootElement);
      } else if (tRootElement instanceof TMessage tMessage) {
        MessageMapper messageMapper = bpmnMapperFactory.createMessageMapper();
        messages.put(tMessage.getId(), messageMapper.map(tMessage));
      } else if (tRootElement instanceof TEscalation tEscalation) {
        EscalationMapper escalationMapper = bpmnMapperFactory.createEscalationMapper();
        escalations.put(tEscalation.getId(), escalationMapper.map(tEscalation));
      }
    }

    return builder.build();
  }
}
