package nl.qunit.bpmnmeister.engine.persistence.processinstance;

import java.util.Set;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@BsonDiscriminator
@Getter
@SuperBuilder
public class ParallelGatewayState extends BpmnElementState {
  Set<String> triggeredFlows;
}
