package io.taktx.client;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.taktx.dto.ExternalTaskTriggerDTO;
import io.taktx.dto.VariablesDTO;
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
