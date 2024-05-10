package nl.qunit.bpmnmeister.pd.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import nl.qunit.bpmnmeister.pi.state.ActivityStateEnum;
import nl.qunit.bpmnmeister.pi.state.ServiceTaskState;

@Getter
@EqualsAndHashCode(callSuper = true)
public class ServiceTask extends Task<ServiceTaskState> {

  private final String workerDefinition;
  private final String retries;
  private final String implementation;
  private final Map<String, String> headers;

  @JsonCreator
  public ServiceTask(
      @Nonnull @JsonProperty("id") String id,
      @Nonnull @JsonProperty("parentId") String parentId,
      @Nonnull @JsonProperty("workerDefinition") String workerDefinition,
      @Nonnull @JsonProperty("retries") String retries,
      @Nonnull @JsonProperty("incoming") Set<String> incoming,
      @Nonnull @JsonProperty("outgoing") Set<String> outgoing,
      @Nonnull @JsonProperty("implementation") String implementation,
      @Nonnull @JsonProperty("loopCharacteristics") LoopCharacteristics loopCharacteristics,
      @Nonnull @JsonProperty("headers") Map<String, String> headers) {
    super(id, parentId, incoming, outgoing, loopCharacteristics);
    this.workerDefinition = workerDefinition;
    this.retries = retries;
    this.implementation = implementation;
    this.headers = headers;
  }

  public ServiceTaskState getInitialState() {
    return new ServiceTaskState(ActivityStateEnum.READY, UUID.randomUUID(), 0, 0, 0);
  }

  @Override
  protected FlowElement withoutLoopCharacteristics(Set<String> outgoing) {
    return new ServiceTask(
        getId(),
        getId(),
        getWorkerDefinition(),
        getRetries(),
        getIncoming(),
        outgoing,
        getImplementation(),
        LoopCharacteristics.NONE,
        getHeaders());
  }
}
