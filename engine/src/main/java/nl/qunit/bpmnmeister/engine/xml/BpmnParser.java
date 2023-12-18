package nl.qunit.bpmnmeister.engine.xml;

import io.quarkus.runtime.Startup;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;

import java.awt.print.Book;
import java.io.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;

import lombok.RequiredArgsConstructor;
import nl.qunit.bpmnmeister.bpmn.TDefinitions;
import nl.qunit.bpmnmeister.engine.ProcessInstanceProcessor;
import nl.qunit.bpmnmeister.model.processdefinition.ProcessDefinition;
import nl.qunit.bpmnmeister.model.processinstance.ProcessInstance;
import nl.qunit.bpmnmeister.model.processinstance.Trigger;

@ApplicationScoped
@RequiredArgsConstructor
public class BpmnParser {
    private final BpmnMapper bpmnMapper;
    private final ProcessInstanceProcessor processor;

    @Startup
    void parse() throws JAXBException {
        JAXBContext context = JAXBContext.newInstance(TDefinitions.class);
        Unmarshaller un = context.createUnmarshaller();
        JAXBElement<TDefinitions> definitions = (JAXBElement<TDefinitions>) un.unmarshal(new File("/Users/erichendriks/IdeaProjects/gaas-rpss-autonomous-grid-manager/app-cam8/src/main/resources/bpmn/diagram_4.bpmn"));

        ProcessDefinition processDefinition = bpmnMapper.map(definitions.getValue());
        ProcessInstance processInstance = new ProcessInstance(UUID.randomUUID(), new HashMap<>());
        Trigger trigger = new Trigger("StartEvent_1", null);
        processor.trigger(processDefinition, processInstance, trigger);
    }
}
