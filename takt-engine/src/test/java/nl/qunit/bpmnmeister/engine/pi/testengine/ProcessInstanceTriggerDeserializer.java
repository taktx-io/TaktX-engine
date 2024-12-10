package nl.qunit.bpmnmeister.engine.pi.testengine;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import io.quarkus.kafka.client.serialization.ObjectMapperDeserializer;
import nl.qunit.bpmnmeister.pi.trigger.v_1_0_0.ProcessInstanceTriggerDTO;

public class ProcessInstanceTriggerDeserializer
    extends ObjectMapperDeserializer<ProcessInstanceTriggerDTO> {

  public ProcessInstanceTriggerDeserializer() {
    super(ProcessInstanceTriggerDTO.class, new ObjectMapper(new CBORFactory()));
  }
}
