package nl.qunit.bpmnmeister.engine.persistence.processdefinition;

import lombok.Getter;
import lombok.experimental.SuperBuilder;
import org.bson.codecs.pojo.annotations.BsonCreator;
import org.bson.codecs.pojo.annotations.BsonDiscriminator;
import org.bson.codecs.pojo.annotations.BsonId;
import org.bson.codecs.pojo.annotations.BsonProperty;

@SuperBuilder
@Getter
@BsonDiscriminator
public class TimerEventDefinition extends EventDefinition {
  private final String timeDate;
  private final String timeDuration;
  private final String timeCycle;

  @BsonCreator
  public TimerEventDefinition(
      @BsonId String id,
      @BsonProperty("timeDate") String timeDate,
      @BsonProperty("timeDuration") String timeDuration,
      @BsonProperty("timeCycle") String timeCycle) {
    super(id);
    this.timeDate = timeDate;
    this.timeDuration = timeDuration;
    this.timeCycle = timeCycle;
  }
}
