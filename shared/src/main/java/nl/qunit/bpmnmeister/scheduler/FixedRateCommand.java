package nl.qunit.bpmnmeister.scheduler;

import nl.qunit.bpmnmeister.pi.ProcessInstanceTrigger;

import java.util.List;

public record FixedRateCommand(
    ScheduleKey scheduleKey,
    List<ProcessInstanceTrigger> triggers,
    String period,
    int repetitions,
    int repeatedCnt)
    implements ScheduleCommand {

  public static final String PERIOD = "kafka-scheduler-period";
}
