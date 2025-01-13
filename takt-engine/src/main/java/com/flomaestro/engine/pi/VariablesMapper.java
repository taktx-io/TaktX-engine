package com.flomaestro.engine.pi;

import com.fasterxml.jackson.databind.JsonNode;
import com.flomaestro.engine.pi.model.Variables;
import com.flomaestro.takt.dto.v_1_0_0.VariablesDTO;
import java.util.Map;
import java.util.stream.Collectors;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "jakarta")
public interface VariablesMapper {

  @Mapping(source = "variables", target = "variables")
  VariablesDTO toDTO(Variables variables);

  default Map<String, JsonNode> map(Variables value) {
    return value.entrySet().stream()
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }
}
