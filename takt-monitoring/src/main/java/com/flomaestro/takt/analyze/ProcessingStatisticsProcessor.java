package com.flomaestro.takt.analyze;

import com.flomaestro.takt.dto.v_1_0_0.ProcessingStatisticsDTO;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;

@Slf4j
public class ProcessingStatisticsProcessor
    implements Processor<String, ProcessingStatisticsDTO, Object, Object> {

  private final IDataService dataService;

  public ProcessingStatisticsProcessor(IDataService dataService) {
    this.dataService = dataService;
  }

  @Override
  public void init(ProcessorContext<Object, Object> context) {}

  @Override
  public void process(Record<String, ProcessingStatisticsDTO> triggerRecord) {
    log.info("Processing statistics: {} {}", triggerRecord.key(), triggerRecord.value());
    try {
      dataService.createData(triggerRecord.timestamp(), triggerRecord.value());
    } catch (DataServiceException e) {
      throw new IllegalStateException(e);
    }
  }
}
