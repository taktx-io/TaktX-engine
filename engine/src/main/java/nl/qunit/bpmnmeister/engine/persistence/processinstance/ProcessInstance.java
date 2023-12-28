package nl.qunit.bpmnmeister.engine.persistence.processinstance;

import io.quarkus.mongodb.panache.common.MongoEntity;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import org.bson.types.ObjectId;

@MongoEntity(collection = "ProcessInstance")
@SuperBuilder
@Getter
public class ProcessInstance {
  public ObjectId id;
  public UUID processInstanceId;
  public String processDefinitionId;
  public long version;
  public Map<String, BpmnElementState> elementStates;
}
