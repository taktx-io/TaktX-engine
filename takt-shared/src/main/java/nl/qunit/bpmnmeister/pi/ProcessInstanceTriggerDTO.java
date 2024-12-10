package nl.qunit.bpmnmeister.pi;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.ToString;
import nl.qunit.bpmnmeister.pi.state.VariablesDTO;

@JsonTypeInfo(use = Id.CLASS, property = "clazz")
@ToString
@Getter
public abstract class ProcessInstanceTriggerDTO {

  private final UUID processInstanceKey;
  private final List<String> elementIdPath;
  private final VariablesDTO variables;

  protected ProcessInstanceTriggerDTO(
      UUID processInstanceKey, List<String> elementIdPath, VariablesDTO variables) {
    this.processInstanceKey = processInstanceKey;
    this.elementIdPath = elementIdPath;
    this.variables = variables;
  }
}
