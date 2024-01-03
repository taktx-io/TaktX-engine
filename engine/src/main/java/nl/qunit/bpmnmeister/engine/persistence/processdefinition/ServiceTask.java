package nl.qunit.bpmnmeister.engine.persistence.processdefinition;

import lombok.Getter;
import lombok.experimental.SuperBuilder;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.*;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@Getter
@BsonDiscriminator
@SuperBuilder
public class ServiceTask extends Task {}
