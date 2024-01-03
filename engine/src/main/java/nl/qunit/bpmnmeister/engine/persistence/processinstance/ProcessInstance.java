package nl.qunit.bpmnmeister.engine.persistence.processinstance;

import io.quarkus.mongodb.panache.common.MongoEntity;
import java.util.Map;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.SuperBuilder;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.state.BpmnElementState;
import org.bson.codecs.pojo.annotations.BsonCreator;
import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.codecs.pojo.annotations.BsonProperty;
import org.bson.types.ObjectId;

@MongoEntity(collection = "ProcessInstance")
@SuperBuilder
@Getter
public class ProcessInstance {
  @Setter ObjectId id;
  UUID processInstanceId;
  String processDefinitionId;
  long version;
  Map<String, BpmnElementState> elementStates;

  @BsonCreator
  public ProcessInstance(
      @BsonId ObjectId id,
      @BsonProperty("processInstanceId") UUID processInstanceId,
      @BsonProperty("processDefinitionId") String processDefinitionId,
      @BsonProperty("version") long version,
      @BsonProperty("elementStates") Map<String, BpmnElementState> elementStates) {
    this.id = id;
    this.processInstanceId = processInstanceId;
    this.processDefinitionId = processDefinitionId;
    this.version = version;
    this.elementStates = elementStates;
  }
}
