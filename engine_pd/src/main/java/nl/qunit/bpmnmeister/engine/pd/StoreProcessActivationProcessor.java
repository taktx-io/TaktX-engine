package nl.qunit.bpmnmeister.engine.pd;

import static nl.qunit.bpmnmeister.engine.pd.Stores.*;

import nl.qunit.bpmnmeister.pd.model.ProcessDefinition;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionKey;
import nl.qunit.bpmnmeister.pd.model.ProcessDefinitionStateEnum;
import nl.qunit.bpmnmeister.pi.ProcessActivation;
import nl.qunit.bpmnmeister.scheduler.ScheduleCommand;
import nl.qunit.bpmnmeister.scheduler.ScheduleKey;
import nl.qunit.bpmnmeister.scheduler.ScheduleType;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueStore;

public class StoreProcessActivationProcessor
    implements Processor<ProcessDefinitionKey, ProcessActivation, ScheduleKey, ScheduleCommand> {

  private ProcessorContext<ScheduleKey, ScheduleCommand> context;
  private final ScheduleCommandFactory timerDefinitionScheduler;

  public StoreProcessActivationProcessor(ScheduleCommandFactory timerDefinitionScheduler) {
    this.timerDefinitionScheduler = timerDefinitionScheduler;
  }

  @Override
  public void init(ProcessorContext<ScheduleKey, ScheduleCommand> context) {
    this.context = context;
  }

  @Override
  public void process(Record<ProcessDefinitionKey, ProcessActivation> processActivationRecord) {
    KeyValueStore<ProcessDefinitionKey, ProcessActivation> stateStore =
        context.getStateStore(PROCESS_ACTIVATION_STORE_NAME);
    if (processActivationRecord.value().getState() == ProcessDefinitionStateEnum.ACTIVE) {
      stateStore.put(processActivationRecord.key(), processActivationRecord.value());
      ProcessActivation processActivation = stateStore.get(processActivationRecord.key());
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
                              ScheduleKey.builder()
                                  .processDefinitionKey(processActivationRecord.key())
                                  .scheduleType(ScheduleType.from(timerEventDefinition))
                                  .elementId(startEvent.getId())
                                  .timerEventDefinitionId(timerEventDefinition.getId())
                                  .build();
                          context.forward(
                              new Record<>(
                                  scheduleKey,
                                  timerDefinitionScheduler.schedule(
                                      processDefinition, startEvent, timerEventDefinition),
                                  processActivationRecord.timestamp()));
                        });
              });
    } else if (processActivationRecord.value().getState() == ProcessDefinitionStateEnum.INACTIVE) {
      ProcessActivation processActivation = stateStore.get(processActivationRecord.key());
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
                              ScheduleKey.builder()
                                  .processDefinitionKey(processActivationRecord.key())
                                  .elementId(startEvent.getId())
                                  .scheduleType(ScheduleType.from(timerEventDefinition))
                                  .timerEventDefinitionId(timerEventDefinition.getId())
                                  .build();
                          context.forward(
                              new Record<>(scheduleKey, null, processActivationRecord.timestamp()));
                        });
              });
    }
  }
}
