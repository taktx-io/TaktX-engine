package nl.qunit.bpmnmeister.engine.pd;

import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionKey;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionStateEnum;
import nl.qunit.bpmnmeister.pi.ProcessDefinitionActivation;
import nl.qunit.bpmnmeister.scheduler.MessageScheduler;
import nl.qunit.bpmnmeister.scheduler.ScheduleKey;
import nl.qunit.bpmnmeister.scheduler.ScheduleType;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;

public class ProcessDefinitionActivationProcessor
    implements Processor<
        ProcessDefinitionKey, ProcessDefinitionActivation, ScheduleKey, MessageScheduler> {

  private ProcessorContext<ScheduleKey, MessageScheduler> context;
  private final StartCommandScheduler startCommandScheduler;

  public ProcessDefinitionActivationProcessor(StartCommandScheduler startCommandScheduler) {
    this.startCommandScheduler = startCommandScheduler;
  }

  @Override
  public void init(ProcessorContext<ScheduleKey, MessageScheduler> context) {
    this.context = context;
  }

  @Override
  public void process(
      Record<ProcessDefinitionKey, ProcessDefinitionActivation> processActivationRecord) {
    if (processActivationRecord.value().getState() == ProcessDefinitionStateEnum.ACTIVE) {
      ProcessDefinitionActivation processActivation = processActivationRecord.value();
      ProcessDefinition processDefinition = processActivation.getProcessDefinition();
      processDefinition
          .getDefinitions()
          .getRootProcess()
          .getFlowElements()
          .getStartEvents()
          .forEach(
              startEvent ->
                  startEvent
                      .getTimerEventDefinitions()
                      .forEach(
                          timerEventDefinition -> {
                            ScheduleKey scheduleKey =
                                new ScheduleKey(
                                    processActivationRecord.key(),
                                    ScheduleType.from(timerEventDefinition),
                                    startEvent.getId(),
                                    timerEventDefinition.getId());
                            context.forward(
                                new Record<>(
                                    scheduleKey,
                                    startCommandScheduler.schedule(
                                        processDefinition, timerEventDefinition),
                                    processActivationRecord.timestamp()));
                          }));
    } else if (processActivationRecord.value().getState() == ProcessDefinitionStateEnum.INACTIVE) {
      ProcessDefinitionActivation processActivation = processActivationRecord.value();
      ProcessDefinition processDefinition = processActivation.getProcessDefinition();
      processDefinition
          .getDefinitions()
          .getRootProcess()
          .getFlowElements()
          .getStartEvents()
          .forEach(
              startEvent ->
                  startEvent
                      .getTimerEventDefinitions()
                      .forEach(
                          timerEventDefinition -> {
                            ScheduleKey scheduleKey =
                                new ScheduleKey(
                                    processActivationRecord.key(),
                                    ScheduleType.from(timerEventDefinition),
                                    startEvent.getId(),
                                    timerEventDefinition.getId());
                            context.forward(
                                new Record<>(
                                    scheduleKey, null, processActivationRecord.timestamp()));
                          }));
    }
  }
}
