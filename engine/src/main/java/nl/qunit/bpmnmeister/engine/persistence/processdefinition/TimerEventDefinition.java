package nl.qunit.bpmnmeister.engine.persistence.processdefinition;

import lombok.Getter;
import lombok.experimental.SuperBuilder;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;

@SuperBuilder
@Getter
@BsonDiscriminator
public class TimerEventDefinition extends EventDefinition {
  private final String timeDate;
  private final String timeDuration;
  private final String timeCycle;

  public TimerEventDefinition(String id, String timeDate, String timeDuration, String timeCycle) {
    super(id);
    this.timeDate = timeDate;
    this.timeDuration = timeDuration;
    this.timeCycle = timeCycle;
  }
}
