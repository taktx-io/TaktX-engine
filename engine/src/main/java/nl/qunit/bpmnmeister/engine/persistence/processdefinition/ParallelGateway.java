package nl.qunit.bpmnmeister.engine.persistence.processdefinition;

import lombok.Getter;
import lombok.experimental.SuperBuilder;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.*;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@BsonDiscriminator
@Getter
@SuperBuilder
public class ParallelGateway extends Gateway {}
