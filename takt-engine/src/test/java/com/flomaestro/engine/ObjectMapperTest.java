package com.flomaestro.engine;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.dataformat.cbor.CBORFactory;
import com.flomaestro.takt.dto.v_1_0_0.DefinitionsKey;
import com.flomaestro.takt.dto.v_1_0_0.ErrorDTO;
import com.flomaestro.takt.dto.v_1_0_0.EscalationDTO;
import com.flomaestro.takt.dto.v_1_0_0.FlowElementsDTO;
import com.flomaestro.takt.dto.v_1_0_0.MessageDTO;
import com.flomaestro.takt.dto.v_1_0_0.ParsedDefinitionsDTO;
import com.flomaestro.takt.dto.v_1_0_0.ProcessDTO;
import com.flomaestro.takt.dto.v_1_0_0.ProcessDefinitionDTO;
import com.flomaestro.takt.dto.v_1_0_0.ProcessDefinitionStateEnum;
import com.flomaestro.takt.dto.v_1_0_0.StartNewProcessInstanceTriggerDTO;
import com.flomaestro.takt.dto.v_1_0_0.VariablesDTO;
import java.io.IOException;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;

public class ObjectMapperTest {

  @Test
  void test() throws IOException {
    ObjectMapper objectMapper = new ObjectMapper(new CBORFactory());
    objectMapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);

    Map<String, ErrorDTO> errors = Map.of();
    Map<String, EscalationDTO> escalations = Map.of();
    Map<String, MessageDTO> messages = Map.of();
    FlowElementsDTO flowElements = new FlowElementsDTO(Map.of());
    ProcessDTO process = new ProcessDTO("processId", "processName", flowElements);
    DefinitionsKey definitionKey = new DefinitionsKey("definitionId", "definitionName");
    ParsedDefinitionsDTO definitions =
        new ParsedDefinitionsDTO(definitionKey, process, messages, escalations, errors);
    StartNewProcessInstanceTriggerDTO triggerDTO =
        new StartNewProcessInstanceTriggerDTO(
            UUID.randomUUID(),
            UUID.randomUUID(),
            List.of("parentElementIdPath"),
            List.of(UUID.randomUUID()),
            new ProcessDefinitionDTO(definitions, 1, ProcessDefinitionStateEnum.ACTIVE),
            List.of("xxx"),
            VariablesDTO.of("key", "value"));

    byte[] serializedBytes = objectMapper.writeValueAsBytes(triggerDTO);
    String bytesAsString = new String(serializedBytes);
    String encode = Base64.getEncoder().encodeToString(serializedBytes);

    StartNewProcessInstanceTriggerDTO deserialized =
        objectMapper.readValue(serializedBytes, StartNewProcessInstanceTriggerDTO.class);

    assertThat(deserialized).isEqualTo(triggerDTO);
  }
}
