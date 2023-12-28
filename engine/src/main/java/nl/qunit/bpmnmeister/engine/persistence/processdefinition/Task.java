package nl.qunit.bpmnmeister.engine.persistence.processdefinition;

import java.util.Set;
import lombok.Getter;
import lombok.experimental.SuperBuilder;
import nl.qunit.bpmnmeister.engine.persistence.processinstance.*;
import org.bson.codecs.pojo.annotations.BsonCreator;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;
import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.codecs.pojo.annotations.BsonProperty;

@Getter
@SuperBuilder
@BsonDiscriminator
public class Task extends Activity {
  @BsonCreator
  public Task(
      @BsonId String id,
      @BsonProperty("incoming") Set<String> incoming,
      @BsonProperty("outgoing") Set<String> outgoing) {
    super(id, incoming, outgoing);
  }

  @Override
  public TriggerResult trigger(Trigger trigger, BpmnElementState bpmnElementState) {
    return new TriggerResult(TaskState.builder().build(), getOutgoing(), Set.of());
  }
}
