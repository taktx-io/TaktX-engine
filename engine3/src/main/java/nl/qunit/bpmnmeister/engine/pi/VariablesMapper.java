package nl.qunit.bpmnmeister.engine.pi;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.stream.Collectors;
import nl.qunit.bpmnmeister.pi.Variables2;
import nl.qunit.bpmnmeister.pi.VariablesDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "jakarta")
public interface VariablesMapper {

  @Mapping(source = "variables", target = "variables")
  VariablesDTO toDTO(Variables2 variables);

  @Mapping(source = "variables", target = "variables")
  Variables2 fromDTO(VariablesDTO variablesDTO);

  default Map<String, JsonNode> map(Variables2 value) {
    return value.entrySet().stream()
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  default Variables2 map(Map<String, JsonNode> value) {
    return new Variables2(value);
  }
}
