package nl.qunit.bpmnmeister.pd.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.annotation.Nonnull;
import java.util.Set;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import nl.qunit.bpmnmeister.pi.state.FlowNodeState;

@Getter
@EqualsAndHashCode(callSuper = true)
public abstract class Activity<S extends FlowNodeState> extends FlowNode<S> implements WithIoMapping {
  private final LoopCharacteristics loopCharacteristics;
  private final InputOutputMapping ioMapping;

  protected Activity(
      @Nonnull String id,
      @Nonnull String parentId,
      @Nonnull Set<String> incoming,
      @Nonnull Set<String> outgoing,
      @Nonnull LoopCharacteristics loopCharacteristics,
      @Nonnull InputOutputMapping ioMapping) {
    super(id, parentId, incoming, outgoing);
    this.loopCharacteristics = loopCharacteristics;
    this.ioMapping = ioMapping;
  }

  @JsonIgnore
  public abstract S getInitialState(UUID parentElementInstanceId, String elementId, String inputFlowId, int passedCnt);

    @JsonIgnore
  public abstract ProcessDefinition getAsSubProcessDefinition(
      ProcessDefinition parentProcessDefinition);
}
