package nl.qunit.bpmnmeister.engine.pd;

import io.quarkus.kafka.client.serialization.ObjectMapperSerde;
import nl.qunit.bpmnmeister.pi.ProcessInstanceTrigger;

public class ProcessInstanceTriggerSerde extends ObjectMapperSerde<ProcessInstanceTrigger> {
  ProcessInstanceTriggerSerde() {
    super(ProcessInstanceTrigger.class);
  }
}
