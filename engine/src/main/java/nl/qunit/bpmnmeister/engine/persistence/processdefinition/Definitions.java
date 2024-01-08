package nl.qunit.bpmnmeister.engine.persistence.processdefinition;

import io.quarkus.mongodb.panache.common.MongoEntity;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import org.bson.codecs.pojo.annotations.*;
import org.bson.types.ObjectId;

@BsonDiscriminator
@MongoEntity(collection = "ProcessDefinition")
@Builder
@Getter
public class Definitions {
  @Setter private ObjectId id;
  private ProcessDefinitionState state;
  private ObjectId xmlObjectId;
  private String processDefinitionId;
  private long version;
  private Map<String, BaseElement> elements;

  @BsonCreator
  public Definitions(
      @BsonId ObjectId id,
      @BsonProperty("state") ProcessDefinitionState state,
      @BsonProperty("xmlObjectId") ObjectId xmlObjectId,
      @BsonProperty("processDefinitionId") String processDefinitionId,
      @BsonProperty("version") long version,
      @BsonProperty("elements") Map<String, BaseElement> elements) {
    this.id = id;
    this.state = state;
    this.xmlObjectId = xmlObjectId;
    this.processDefinitionId = processDefinitionId;
    this.version = version;
    this.elements = elements;
  }

  @BsonIgnore
  public List<StartEvent> getStartEvents() {
    return elements.values().stream()
        .filter(Process.class::isInstance)
        .map(Process.class::cast)
        .flatMap(process -> process.getFlowElements().values().stream())
        .filter(StartEvent.class::isInstance)
        .map(StartEvent.class::cast)
        .toList();
  }

  @BsonIgnore
  public Optional<FlowElement> getFlowElement(String id) {
    return elements.values().stream()
        .filter(Process.class::isInstance)
        .map(Process.class::cast)
        .flatMap(process -> process.getFlowElements().values().stream())
        .filter(flowElement -> id.equals(flowElement.getId()))
        .findFirst();
  }
}
