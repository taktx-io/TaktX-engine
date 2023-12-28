package nl.qunit.bpmnmeister.engine.persistence.processdefinition;

import java.util.Map;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import org.bson.codecs.pojo.annotations.BsonCreator;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;
import org.bson.codecs.pojo.annotations.BsonProperty;

@SuperBuilder
@BsonDiscriminator
@Getter
public class Process extends RootElement {
  private Map<String, FlowElement> flowElements;

  @BsonCreator
  public Process(
      @BsonProperty("id") String id,
      @BsonProperty("flowElements") Map<String, FlowElement> flowElements) {
    super(id);
    this.flowElements = flowElements;
  }
}
