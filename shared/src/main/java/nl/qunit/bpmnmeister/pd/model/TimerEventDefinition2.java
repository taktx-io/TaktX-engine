package nl.qunit.bpmnmeister.pd.model;

import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
public class TimerEventDefinition2 extends EventDefinition2 {
  private String timeDate;
  private String timeDuration;
  private String timeCycle;
}
