package nl.qunit.bpmnmeister.engine.xml;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.xml.bind.JAXBElement;
import java.util.*;
import lombok.RequiredArgsConstructor;
import nl.qunit.bpmnmeister.bpmn.*;
import nl.qunit.bpmnmeister.engine.persistence.processdefinition.BpmnElement;
import nl.qunit.bpmnmeister.engine.persistence.processdefinition.ProcessDefinition;
import nl.qunit.bpmnmeister.engine.persistence.processdefinition.SequenceFlow;

@ApplicationScoped
@RequiredArgsConstructor
public class BpmnMapper {
  private final BpmnElementMapper elementMapper;
  private final SequenceFlowMapper sequenceFlowMapper;

  public ProcessDefinition map(TDefinitions definitions) {
    Map<String, BpmnElement> bpmnElements = new HashMap<>();
    Map<String, SequenceFlow> flows = new HashMap<>();
    String id = "unknown";
    for (JAXBElement<? extends TRootElement> jaxbElement : definitions.getRootElement()) {
      TRootElement rootElement = jaxbElement.getValue();
      if (rootElement instanceof TProcess process) {
        id = process.getId();
        for (JAXBElement<? extends TFlowElement> element : process.getFlowElement()) {
          TFlowElement flowElement = element.getValue();
          Optional<BpmnElement> optBpmnElement = elementMapper.map(flowElement);
          optBpmnElement.ifPresent(
              bpmnElement -> bpmnElements.put(bpmnElement.getId(), bpmnElement));
          Optional<SequenceFlow> optSequenceFlow = sequenceFlowMapper.map(flowElement);
          optSequenceFlow.ifPresent(sequenceFlow -> flows.put(sequenceFlow.getId(), sequenceFlow));
        }
      }
    }

    return new ProcessDefinition(null, null, id, -1, bpmnElements, flows);
  }
}
