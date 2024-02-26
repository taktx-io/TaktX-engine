package nl.qunit.bpmnmeister.engine.pd;

import static nl.qunit.bpmnmeister.engine.pd.Stores.*;

import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionKey;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionStateEnum;
import nl.qunit.bpmnmeister.pi.ProcessDefinitionActivation;
import nl.qunit.bpmnmeister.scheduler.ScheduleKey;
import nl.qunit.bpmnmeister.scheduler.ScheduleStartCommand;
import nl.qunit.bpmnmeister.scheduler.ScheduleType;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueStore;

public class StoreProcessDefinitionActivationProcessor
    implements Processor<
        ProcessDefinitionKey, ProcessDefinitionActivation, ScheduleKey, ScheduleStartCommand> {

  private ProcessorContext<ScheduleKey, ScheduleStartCommand> context;
  private final ScheduleCommandFactory timerDefinitionScheduler;

  public StoreProcessDefinitionActivationProcessor(
      ScheduleCommandFactory timerDefinitionScheduler) {
    this.timerDefinitionScheduler = timerDefinitionScheduler;
  }

  @Override
  public void init(ProcessorContext<ScheduleKey, ScheduleStartCommand> context) {
    this.context = context;
  }

  @Override
  public void process(
      Record<ProcessDefinitionKey, ProcessDefinitionActivation> processActivationRecord) {
    KeyValueStore<ProcessDefinitionKey, ProcessDefinitionActivation> stateStore =
        context.getStateStore(PROCESS_DEFINITION_ACTIVATION_STORE_NAME);
    if (processActivationRecord.value().getState() == ProcessDefinitionStateEnum.ACTIVE) {
      stateStore.put(processActivationRecord.key(), processActivationRecord.value());
      ProcessDefinitionActivation processActivation = stateStore.get(processActivationRecord.key());
      ProcessDefinition processDefinition = processActivation.getProcessDefinition();
      processDefinition
          .getStartEvents()
          .forEach(
              startEvent -> {
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
                                  timerDefinitionScheduler.schedule(
                                      processDefinition, startEvent, timerEventDefinition),
                                  processActivationRecord.timestamp()));
                        });
              });
    } else if (processActivationRecord.value().getState() == ProcessDefinitionStateEnum.INACTIVE) {
      ProcessDefinitionActivation processActivation = stateStore.get(processActivationRecord.key());
      ProcessDefinition processDefinition = processActivation.getProcessDefinition();
      processDefinition
          .getStartEvents()
          .forEach(
              startEvent -> {
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
                              new Record<>(scheduleKey, null, processActivationRecord.timestamp()));
                        });
              });
    }
  }
}
