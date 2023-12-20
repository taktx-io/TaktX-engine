package nl.qunit.bpmnmeister.engine.persistence.processdefinition;

import io.quarkus.mongodb.panache.common.MongoEntity;
import java.util.Map;
import lombok.Builder;
import org.bson.types.ObjectId;

@Builder
@MongoEntity(collection = "ProcessDefinition")
public class ProcessDefinition {
  public ObjectId id;
  public ObjectId xmlObjectId;
  public String processDefinitionId;
  public long version;
  public Map<String, BpmnElement> bpmnElements;
  public Map<String, SequenceFlow> flows;
}
