package io.taktx.client.serdes;

import static org.assertj.core.api.Assertions.assertThat;

import io.taktx.dto.v_1_0_0.ProcessDefinitionDTO;
import org.junit.jupiter.api.Test;

class ProcessDefinitionJsonDeserializerTest {
  @Test
  void testConstruct() {
    try (ProcessDefinitionJsonDeserializer deserializer = new ProcessDefinitionJsonDeserializer()) {
      assertThat(deserializer.getClazz()).isEqualTo(ProcessDefinitionDTO.class);
    }
  }
}
