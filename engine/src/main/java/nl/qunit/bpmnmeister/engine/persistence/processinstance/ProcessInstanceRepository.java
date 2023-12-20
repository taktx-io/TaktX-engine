package nl.qunit.bpmnmeister.engine.persistence.processinstance;

import io.quarkus.mongodb.panache.PanacheMongoRepository;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class ProcessInstanceRepository implements PanacheMongoRepository<ProcessInstance> {}
