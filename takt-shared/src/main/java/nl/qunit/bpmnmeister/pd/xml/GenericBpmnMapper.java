package nl.qunit.bpmnmeister.pd.xml;

import jakarta.xml.bind.JAXBElement;
import java.util.HashMap;
import nl.qunit.bpmnmeister.bpmn.TDefinitions;
import nl.qunit.bpmnmeister.bpmn.TError;
import nl.qunit.bpmnmeister.bpmn.TEscalation;
import nl.qunit.bpmnmeister.bpmn.TMessage;
import nl.qunit.bpmnmeister.bpmn.TProcess;
import nl.qunit.bpmnmeister.bpmn.TRootElement;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.DefinitionsKey;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.ErrorDTO;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.EscalationDTO;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.MessageDTO;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.ParsedDefinitionsDTO;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.ParsedDefinitionsDTO.ParsedDefinitionsDTOBuilder;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.ProcessDTO;

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
