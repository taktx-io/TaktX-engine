package nl.qunit.bpmnmeister.pd.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.Objects;
import java.util.Set;
import lombok.Getter;

@Getter
public class ServiceTask extends Task {

  private final String implementation;

  @JsonCreator
  public ServiceTask(
      @Nonnull @JsonProperty("id") String id,
      @Nonnull @JsonProperty("parentId") String parentId,
      @Nonnull @JsonProperty("incoming") Set<String> incoming,
      @Nonnull @JsonProperty("outgoing") Set<String> outgoing,
      @Nonnull @JsonProperty("implementation") String implementation,
      @Nonnull @JsonProperty("loopCharacteristics") LoopCharacteristics loopCharacteristics) {
    super(id, parentId, incoming, outgoing, loopCharacteristics);
    this.implementation = implementation;
  }

  @Override
  protected FlowElement withoutLoopCharacteristics(Set<String> outgoing) {
    return new ServiceTask(
        getId(), getId(), getIncoming(), outgoing, getImplementation(), LoopCharacteristics.NONE);
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
    ServiceTask that = (ServiceTask) o;
    return Objects.equals(implementation, that.implementation);
  }

  @Override
  public int hashCode() {
    return Objects.hash(super.hashCode(), implementation);
  }
}
