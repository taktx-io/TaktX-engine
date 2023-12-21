package nl.qunit.bpmnmeister.engine.persistence.processdefinition;

import io.quarkus.mongodb.panache.common.MongoEntity;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.NoArgsConstructor;
import org.bson.types.ObjectId;

@MongoEntity(collection = "ProcessDefinition")
@NoArgsConstructor
@AllArgsConstructor
public class ProcessDefinition {
  public ObjectId id;
  public ObjectId xmlObjectId;
  public String processDefinitionId;
  public long version;
  public Map<String, ? extends BpmnElement> bpmnElements;
  public Map<String, SequenceFlow> flows;
}
