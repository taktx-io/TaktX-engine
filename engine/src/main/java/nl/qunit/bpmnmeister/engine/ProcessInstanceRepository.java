package nl.qunit.bpmnmeister.engine;

import io.quarkus.mongodb.panache.PanacheMongoRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.UUID;

@ApplicationScoped
public class ProcessInstanceRepository
    implements PanacheMongoRepositoryBase<ProcessInstanceEntity, UUID> {}
