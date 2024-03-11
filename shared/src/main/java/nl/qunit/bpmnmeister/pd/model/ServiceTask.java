package nl.qunit.bpmnmeister.pd.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.Set;
import lombok.Getter;

@Getter
public class ServiceTask extends Task {

  private final String implementation;

  @JsonCreator
  public ServiceTask(
      @Nonnull @JsonProperty("id") BaseElementId id,
      @Nonnull @JsonProperty("parentId") BaseElementId parentId,
      @Nonnull @JsonProperty("incoming") Set<BaseElementId> incoming,
      @Nonnull @JsonProperty("outgoing") Set<BaseElementId> outgoing,
      @Nonnull @JsonProperty("implementation") String implementation,
      @Nonnull @JsonProperty("loopCharacteristics") LoopCharacteristics loopCharacteristics) {
    super(id, parentId, incoming, outgoing, loopCharacteristics);
    this.implementation = implementation;
  }

  @Override
  protected FlowElement withoutLoopCharacteristics() {
    return new ServiceTask(
        getId(), getParentId(), getIncoming(), getOutgoing(), getImplementation(), LoopCharacteristics.NONE);
  }
}
