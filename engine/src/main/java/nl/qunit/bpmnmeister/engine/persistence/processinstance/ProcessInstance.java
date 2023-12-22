package nl.qunit.bpmnmeister.engine.persistence.processinstance;

import io.quarkus.mongodb.panache.common.MongoEntity;
import java.util.Map;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;

@MongoEntity(collection = "ProcessInstance")
@NoArgsConstructor
@AllArgsConstructor
public class ProcessInstance {
  public ObjectId id;
  public UUID processInstanceId;
  public String processDefinitionId;
  public long version;
  public Map<String, BpmnElementState> elementStates;
}
