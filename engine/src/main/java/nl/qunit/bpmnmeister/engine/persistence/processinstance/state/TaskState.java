package nl.qunit.bpmnmeister.engine.persistence.processinstance.state;

import lombok.Getter;
import lombok.experimental.SuperBuilder;
import org.bson.codecs.pojo.annotations.BsonCreator;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;
import org.bson.codecs.pojo.annotations.BsonProperty;

@BsonDiscriminator
@Getter
@SuperBuilder
public class TaskState extends BpmnElementState {
  int cnt;

  @BsonCreator
  public TaskState(@BsonProperty("state") StateEnum state, @BsonProperty("cnt") int cnt) {
    super(state);
    this.cnt = cnt;
  }
}
