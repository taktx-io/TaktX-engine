package nl.qunit.bpmnmeister.engine.persistence.processdefinition;

import io.quarkus.mongodb.panache.PanacheMongoRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ProcessDefinitionXmlRepository
    implements PanacheMongoRepository<ProcessDefinitionXml> {}
