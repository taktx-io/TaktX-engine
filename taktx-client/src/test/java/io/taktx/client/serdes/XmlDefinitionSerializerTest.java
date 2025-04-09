package io.taktx.client.serdes;

import static org.assertj.core.api.Assertions.assertThat;

import io.taktx.dto.XmlDefinitionsDTO;
import org.junit.jupiter.api.Test;

class XmlDefinitionSerializerTest {
  @Test
  void testConstruct() {
    try (XmlDefinitionSerializer serializer = new XmlDefinitionSerializer()) {
      assertThat(serializer.getClazz()).isEqualTo(XmlDefinitionsDTO.class);
    }
  }
}
