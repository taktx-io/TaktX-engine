package nl.qunit.bpmnmeister.engine.persistence.processdefinition;

import io.quarkus.mongodb.panache.common.MongoEntity;
import lombok.*;
import org.bson.types.ObjectId;

@MongoEntity(collection = "ProcessDefinitionXml")
@Builder
public class ProcessDefinitionXml {
  public ObjectId id;
  public String processDefinitionId;
  public String xml;
  public int hash;
}
