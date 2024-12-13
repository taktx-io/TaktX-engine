package com.flomaestro.takt.util;

import com.flomaestro.takt.dto.v_1_0_0.VariableKeyDTO;
import java.util.UUID;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class VariableKeySerdeTest {

  @Test
  void test() {
    VariableKeySerde serde = new VariableKeySerde();
    VariableKeyDTO variableKey = new VariableKeyDTO(UUID.randomUUID(), "variableName");
    byte[] serialize = serde.serializer().serialize("topic", variableKey);
    VariableKeyDTO deserialize = serde.deserializer().deserialize("topic", serialize);
    Assertions.assertThat(deserialize).isEqualTo(variableKey);
  }
}
