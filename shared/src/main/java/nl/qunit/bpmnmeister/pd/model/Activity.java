package nl.qunit.bpmnmeister.pd.model;

import jakarta.annotation.Nonnull;
import java.util.Objects;
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

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    if (!super.equals(o)) {
      return false;
    }
    Activity activity = (Activity) o;
    return Objects.equals(loopCharacteristics, activity.loopCharacteristics);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), loopCharacteristics);
  }

  public abstract ProcessDefinition getAsSubProcessDefinition(
      ProcessDefinition parentProcessDefinition);

  public abstract BaseElementId getAsSubProcessStartElementId();
}
