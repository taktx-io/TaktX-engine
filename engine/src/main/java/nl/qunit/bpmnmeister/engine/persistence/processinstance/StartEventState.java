package nl.qunit.bpmnmeister.engine.persistence.processinstance;

import lombok.Getter;
import lombok.experimental.SuperBuilder;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@BsonDiscriminator
@Getter
@SuperBuilder
public class StartEventState extends BpmnElementState {}
