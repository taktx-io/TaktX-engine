package nl.qunit.bpmnmeister.engine.xml;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import java.io.*;
import lombok.RequiredArgsConstructor;
import nl.qunit.bpmnmeister.bpmn.TDefinitions;
import nl.qunit.bpmnmeister.engine.persistence.processdefinition.ProcessDefinition;
import org.apache.camel.util.xml.StringSource;

@ApplicationScoped
@RequiredArgsConstructor
public class BpmnParser {
  private final BpmnMapper bpmnMapper;

  public ProcessDefinition parse(String xml) throws JAXBException {
    JAXBContext context = JAXBContext.newInstance(TDefinitions.class);
    Unmarshaller un = context.createUnmarshaller();
    JAXBElement<TDefinitions> definitions = un.unmarshal(new StringSource(xml), TDefinitions.class);

    return bpmnMapper.map(definitions.getValue());
  }
}
