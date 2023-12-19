package nl.qunit.bpmnmeister.engine;

import io.quarkus.mongodb.panache.common.MongoEntity;
import lombok.Builder;
import org.bson.types.ObjectId;

@MongoEntity(collection = "ProcessInstance")
@Builder
public class ProcessInstanceEntity {
  public ObjectId id; // used by MongoDB for the _id field
  public String processInstanceId;
}
