package nl.qunit.bpmnmeister.pd.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import nl.qunit.bpmnmeister.pi.state.FlowNodeStateEnum;
import nl.qunit.bpmnmeister.pi.state.ServiceTaskState;

@Getter
@EqualsAndHashCode(callSuper = true)
public class ServiceTask extends ExternalTask<ServiceTaskState> {

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
      @Nonnull @JsonProperty("headers") Map<String, String> headers,
      @Nonnull @JsonProperty("ioMapping") InputOutputMapping ioMapping) {
    super(id, parentId, incoming, outgoing, loopCharacteristics, ioMapping, workerDefinition, retries, implementation, headers);
  }

  @Override
  public ServiceTaskState getInitialState(String inputFlowId, int passedCnt) {
    return new ServiceTaskState(
        FlowNodeStateEnum.READY, UUID.randomUUID(), passedCnt, 0, 0, inputFlowId);
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
        getHeaders(),
        getIoMapping());
  }
}
