package nl.qunit.bpmnmeister.engine.pi;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.Map;
import java.util.stream.Collectors;
import nl.qunit.bpmnmeister.engine.pi.model.Variables;
import nl.qunit.bpmnmeister.pi.state.v_1_0_0.VariablesDTO;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "jakarta")
public interface VariablesMapper {

  @Mapping(source = "variables", target = "variables")
  VariablesDTO toDTO(Variables variables);

  @Mapping(source = "variables", target = "variables")
  Variables fromDTO(VariablesDTO variablesDTO);

  default Map<String, JsonNode> map(Variables value) {
    return value.entrySet().stream()
        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }

  default Variables map(Map<String, JsonNode> value) {
    return new Variables(value);
  }
}
