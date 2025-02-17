package com.flomaestro.takt.analyze;

import com.flomaestro.takt.dto.v_1_0_0.InstanceUpdateDTO;
import com.flomaestro.takt.dto.v_1_0_0.ProcessInstanceUpdateDTO;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.streams.processor.PunctuationType;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;

@Slf4j
public class InstanceUpdateProcessor implements Processor<UUID, InstanceUpdateDTO, Object, Object> {

  private static final Map<UUID, Long> processedInstances = new ConcurrentHashMap<>();
  private static final Map<String, List<Long>> averageProcessTimes = new ConcurrentHashMap<>();
  private final IDataService instanceUpdateService;

  public InstanceUpdateProcessor(IDataService instanceUpdateService) {
    this.instanceUpdateService = instanceUpdateService;
  }

  @Override
  public void init(ProcessorContext<Object, Object> context) {
    context.schedule(
        Duration.ofMillis(10000),
        PunctuationType.WALL_CLOCK_TIME,
        timestamp -> {
          averageProcessTimes.forEach(
              (processDefinitionId, processTimes) -> {
                long average =
                    processTimes.stream().mapToLong(Long::longValue).sum() / processTimes.size();
                try {
                  instanceUpdateService.createProcessInstanceData(
                      timestamp, processDefinitionId, average, processTimes.size());
                } catch (DataServiceException e) {
                  throw new RuntimeException(e);
                }
              });
          averageProcessTimes.clear();
        });
  }

  @Override
  public void process(Record<UUID, InstanceUpdateDTO> triggerRecord) {
    log.info("Processing instance update: {} {}", triggerRecord.key(), triggerRecord.value());
    InstanceUpdateDTO instanceUpdate = triggerRecord.value();
    if (instanceUpdate instanceof ProcessInstanceUpdateDTO processInstanceUpdate) {
      if (processInstanceUpdate.getFlowNodeInstances().getState().isFinished()) {
        Long existing = processedInstances.remove(triggerRecord.key());
        if (existing != null) {
          averageProcessTimes
              .computeIfAbsent(
                  processInstanceUpdate.getProcessDefinitionKey().getProcessDefinitionId(),
                  key -> new ArrayList<>())
              .add(processInstanceUpdate.getProcessTime() - existing);
        }
      } else {
        processedInstances.putIfAbsent(triggerRecord.key(), processInstanceUpdate.getProcessTime());
      }
    }
  }
}
