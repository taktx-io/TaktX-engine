package nl.qunit.bpmnmeister.scheduler;

import static com.cronutils.model.CronType.QUARTZ;

import com.cronutils.model.definition.CronDefinition;
import com.cronutils.model.definition.CronDefinitionBuilder;
import com.cronutils.parser.CronParser;
import nl.qunit.bpmnmeister.pd.model.TimerEventDefinitionDTO;

public enum ScheduleType {
  RECURRING,
  ONE_TIME,
  FIXED_RATE;

  public static ScheduleType from(TimerEventDefinitionDTO timerEventDefinition) {
    if (timerEventDefinition.getTimeCycle() != null) {
      return cycleType(timerEventDefinition);
    } else if (timerEventDefinition.getTimeDate() != null) {
      return ScheduleType.ONE_TIME;
    } else if (timerEventDefinition.getTimeDuration() != null) {
      return ScheduleType.RECURRING;
    }
    throw new IllegalArgumentException("TimerEventDefinition is not valid");
  }

  private static ScheduleType cycleType(TimerEventDefinitionDTO timerEventDefinition) {
    if (isValidCron(timerEventDefinition.getTimeCycle())) {
      return ScheduleType.RECURRING;
    } else {
      return ScheduleType.FIXED_RATE;
    }
  }

  private static boolean isValidCron(String timeCycle) {

    // validate expression
    try {
      // get a predefined instance
      CronDefinition cronDefinition = CronDefinitionBuilder.instanceDefinitionFor(QUARTZ);

      // create a parser based on provided definition
      CronParser parser = new CronParser(cronDefinition);
      parser.parse(timeCycle);
      return true;
    } catch (IllegalArgumentException e) {
      return false;
    }
  }
}
