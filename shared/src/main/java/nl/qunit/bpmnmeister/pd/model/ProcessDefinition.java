package nl.qunit.bpmnmeister.pd.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import nl.qunit.bpmnmeister.pi.ElementStates;
import nl.qunit.bpmnmeister.pi.state.BpmnElementState;

@EqualsAndHashCode
@Getter
@ToString
public class ProcessDefinition {

  public static final ProcessDefinition NONE =
      new ProcessDefinition(Definitions.NONE, -1, ProcessDefinitionStateEnum.INACTIVE);

  private final Definitions definitions;
  private final Integer version;
  private final ProcessDefinitionStateEnum state;

  @JsonCreator
  public ProcessDefinition(
      @Nonnull @JsonProperty("definitions") Definitions definitions,
      @Nonnull @JsonProperty("id") Integer version,
      @Nonnull @JsonProperty("state") ProcessDefinitionStateEnum state) {
    this.definitions = definitions;
    this.version = version;
    this.state = state;
  }

  public ElementStates initElementStates() {
    Map<String, BpmnElementState> collect =
        definitions.getRootProcess().getFlowElements().getFlowNodes().stream()
            .collect(Collectors.toMap(FlowNode::getId, FlowNode::getInitialState));
    return new ElementStates(collect);
  }
}
