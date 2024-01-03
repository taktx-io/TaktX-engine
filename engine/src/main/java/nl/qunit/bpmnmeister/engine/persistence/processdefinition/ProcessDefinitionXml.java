package nl.qunit.bpmnmeister.engine.persistence.processdefinition;

import io.quarkus.mongodb.panache.common.MongoEntity;
import lombok.*;
import org.bson.codecs.pojo.annotations.BsonCreator;
import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.codecs.pojo.annotations.BsonProperty;
import org.bson.types.ObjectId;

@MongoEntity(collection = "ProcessDefinitionXml")
@Getter
@Builder
public class ProcessDefinitionXml {
  @Setter private ObjectId id;
  private String processDefinitionId;
  private String xml;
  private int hash;

  @BsonCreator
  public ProcessDefinitionXml(
      @BsonId ObjectId id,
      @BsonProperty("processDefinitionId") String processDefinitionId,
      @BsonProperty("xml") String xml,
      @BsonProperty("hash") int hash) {
    this.id = id;
    this.processDefinitionId = processDefinitionId;
    this.xml = xml;
    this.hash = hash;
  }
}
