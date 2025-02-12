package com.flomaestro.takt.analyze;

import com.flomaestro.takt.dto.v_1_0_0.InstanceUpdateDTO;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;

@Slf4j
public class InstanceUpdateProcessor implements Processor<UUID, InstanceUpdateDTO, Object, Object> {

  private final IDataService dataService;

  public InstanceUpdateProcessor(IDataService dataService) {
    this.dataService = dataService;
  }

  @Override
  public void init(ProcessorContext<Object, Object> context) {}

  @Override
  public void process(Record<UUID, InstanceUpdateDTO> triggerRecord) {
    log.info("Processing instance update: {} {}", triggerRecord.key(), triggerRecord.value());
  }
}
