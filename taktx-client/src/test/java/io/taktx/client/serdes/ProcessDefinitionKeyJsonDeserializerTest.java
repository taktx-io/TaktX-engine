package io.taktx.client.serdes;

import static org.assertj.core.api.Assertions.assertThat;

import io.taktx.dto.ProcessInstanceTriggerDTO;
import org.junit.jupiter.api.Test;

class ProcessDefinitionKeyJsonDeserializerTest {
  @Test
  void testConstruct() {
    try (ProcessInstanceTriggerSerializer serializer = new ProcessInstanceTriggerSerializer()) {
      assertThat(serializer.getClazz()).isEqualTo(ProcessInstanceTriggerDTO.class);
    }
  }
}
