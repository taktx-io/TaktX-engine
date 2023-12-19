package nl.qunit.bpmnmeister.engine.xml;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.xml.bind.JAXBElement;
import java.util.*;
import lombok.RequiredArgsConstructor;
import nl.qunit.bpmnmeister.bpmn.*;
import nl.qunit.bpmnmeister.model.processdefinition.BpmnElement;
import nl.qunit.bpmnmeister.model.processdefinition.ProcessDefinition;
import nl.qunit.bpmnmeister.model.processdefinition.SequenceFlow;

@ApplicationScoped
@RequiredArgsConstructor
public class BpmnMapper {
  private final BpmnElementMapper elementMapper;
  private final SequenceFlowMapper sequenceFlowMapper;

  public ProcessDefinition map(TDefinitions definitions) {
    Map<String, BpmnElement> bpmnElements = new HashMap<>();
    Map<String, SequenceFlow> flows = new HashMap<>();

    for (JAXBElement<? extends TRootElement> jaxbElement : definitions.getRootElement()) {
      TRootElement rootElement = jaxbElement.getValue();
      if (rootElement instanceof TProcess process) {

        for (JAXBElement<? extends TFlowElement> element : process.getFlowElement()) {
          TFlowElement flowElement = element.getValue();
          Optional<BpmnElement> optBpmnElement = elementMapper.map(flowElement);
          optBpmnElement.ifPresent(bpmnElement -> bpmnElements.put(bpmnElement.id(), bpmnElement));
          Optional<SequenceFlow> optSequenceFlow = sequenceFlowMapper.map(flowElement);
          optSequenceFlow.ifPresent(sequenceFlow -> flows.put(sequenceFlow.id(), sequenceFlow));
        }
      }
    }

    TRootElement root = definitions.getRootElement().get(0).getValue();
    return new ProcessDefinition(root.getId(), bpmnElements, flows);
  }
}
