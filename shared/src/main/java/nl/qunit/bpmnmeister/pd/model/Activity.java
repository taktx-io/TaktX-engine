package nl.qunit.bpmnmeister.pd.model;

import jakarta.annotation.Nonnull;
import java.util.Set;
import lombok.Getter;

@Getter
public abstract class Activity extends FlowNode {
  private final LoopCharacteristics loopCharacteristics;

  protected Activity(
      @Nonnull BaseElementId id,
      @Nonnull BaseElementId parentId,
      @Nonnull Set<BaseElementId> incoming,
      @Nonnull Set<BaseElementId> outgoing,
      @Nonnull LoopCharacteristics loopCharacteristics) {
    super(id, parentId, incoming, outgoing);
    this.loopCharacteristics = loopCharacteristics;
  }

  public abstract ProcessDefinition getAsSubProcessDefinition(
      ProcessDefinition parentProcessDefinition);
}
