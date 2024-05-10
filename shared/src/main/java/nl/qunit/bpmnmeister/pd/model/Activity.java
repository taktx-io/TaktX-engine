package nl.qunit.bpmnmeister.pd.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.annotation.Nonnull;
import java.util.Set;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import nl.qunit.bpmnmeister.pi.state.BpmnElementState;

@Getter
@EqualsAndHashCode(callSuper = true)
public abstract class Activity<S extends BpmnElementState> extends FlowNode<S> {
  private final LoopCharacteristics loopCharacteristics;

  protected Activity(
      @Nonnull String id,
      @Nonnull String parentId,
      @Nonnull Set<String> incoming,
      @Nonnull Set<String> outgoing,
      @Nonnull LoopCharacteristics loopCharacteristics) {
    super(id, parentId, incoming, outgoing);
    this.loopCharacteristics = loopCharacteristics;
  }

  @JsonIgnore
  public abstract ProcessDefinition getAsSubProcessDefinition(
      ProcessDefinition parentProcessDefinition);

  @JsonIgnore
  public abstract String getAsSubProcessStartElementId();
}
