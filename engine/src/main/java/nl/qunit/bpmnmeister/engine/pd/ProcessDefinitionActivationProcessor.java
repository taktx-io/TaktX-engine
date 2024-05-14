package nl.qunit.bpmnmeister.engine.pd;

import java.util.ArrayList;
import java.util.List;
import nl.qunit.bpmnmeister.pd.model.Constants;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionKey;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionStateEnum;
import nl.qunit.bpmnmeister.pi.ProcessDefinitionActivation;
import nl.qunit.bpmnmeister.pi.ProcessInstanceKey;
import nl.qunit.bpmnmeister.pi.StartCommand;
import nl.qunit.bpmnmeister.pi.Variables;
import nl.qunit.bpmnmeister.scheduler.MessageScheduler;
import nl.qunit.bpmnmeister.scheduler.SchedulableMessage;
import nl.qunit.bpmnmeister.scheduler.ScheduleKey;
import nl.qunit.bpmnmeister.scheduler.ScheduleType;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;

public class ProcessDefinitionActivationProcessor
    implements Processor<
        ProcessDefinitionKey, ProcessDefinitionActivation, ScheduleKey, MessageScheduler> {

  private ProcessorContext<ScheduleKey, MessageScheduler> context;
  private final MessageSchedulerFactory messageSchedulerFactory;

  public ProcessDefinitionActivationProcessor(MessageSchedulerFactory messageSchedulerFactory) {
    this.messageSchedulerFactory = messageSchedulerFactory;
  }

  @Override
  public void init(ProcessorContext<ScheduleKey, MessageScheduler> context) {
    this.context = context;
  }

  private static List<SchedulableMessage<?>> getStartCommands(ProcessDefinition processDefinition) {
    List<SchedulableMessage<?>> processInstanceStartCommand = new ArrayList<>();
    processInstanceStartCommand.add(
        new StartCommand(
            ProcessInstanceKey.NONE,
            Constants.NONE,
            processDefinition.getDefinitions().getDefinitionsKey().getProcessDefinitionId(),
            Variables.EMPTY));

    return processInstanceStartCommand;
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
                            MessageScheduler schedule =
                                messageSchedulerFactory.schedule(
                                    processActivationRecord.key(),
                                    ProcessInstanceKey.NONE,
                                    startEvent.getId(),
                                    timerEventDefinition,
                                    getStartCommands(processDefinition),
                                    Variables.EMPTY);
                            context.forward(
                                new Record<>(
                                    schedule.getScheduleKey(),
                                    schedule,
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
                                    ProcessInstanceKey.NONE,
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
