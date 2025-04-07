package io.taktx.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.taktx.dto.v_1_0_0.ExternalTaskTriggerDTO;
import io.taktx.dto.v_1_0_0.VariablesDTO;
import org.junit.jupiter.api.Test;

class VariableParameterResolverTest {

  @Test
  void test() {
    VariableParameterResolver resolver =
        new VariableParameterResolver(new ObjectMapper(), String.class, "name");
    VariablesDTO vars = VariablesDTO.of("name", "test");
    ExternalTaskTriggerDTO trigger = ExternalTaskTriggerDTO.builder().variables(vars).build();
    Object resolve = resolver.resolve(trigger);
    assertThat(resolve).isEqualTo("test");
  }
}
