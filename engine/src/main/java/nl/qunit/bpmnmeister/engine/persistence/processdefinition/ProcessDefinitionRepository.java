package nl.qunit.bpmnmeister.engine.persistence.processdefinition;

import io.quarkus.mongodb.panache.PanacheMongoRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@ApplicationScoped
@BsonDiscriminator
public class ProcessDefinitionRepository implements PanacheMongoRepository<ProcessDefinition> {}
