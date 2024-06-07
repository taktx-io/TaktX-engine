package nl.qunit.bpmnmeister.pd.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.Set;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import nl.qunit.bpmnmeister.pi.state.FlowNodeStateEnum;
import nl.qunit.bpmnmeister.pi.state.ReceiveTaskState;

@Getter
@EqualsAndHashCode(callSuper = true)
public class ReceiveTask extends Task<ReceiveTaskState> {

  @Nonnull private final String messageRef;

  @JsonCreator
  public ReceiveTask(
      @Nonnull @JsonProperty("id") String id,
      @Nonnull @JsonProperty("parentId") String parentId,
      @Nonnull @JsonProperty("incoming") Set<String> incoming,
      @Nonnull @JsonProperty("outgoing") Set<String> outgoing,
      @Nonnull @JsonProperty("loopCharacteristics") LoopCharacteristics loopCharacteristics,
      @Nonnull @JsonProperty("messageRef") String messageRef,
      @Nonnull @JsonProperty("ioMapping") InputOutputMapping ioMapping) {
    super(id, parentId, incoming, outgoing, loopCharacteristics, ioMapping);
    this.messageRef = messageRef;
  }

  @Override
  public ReceiveTaskState getInitialState(String inputFlowId, int passedCnt) {
    return new ReceiveTaskState(
        FlowNodeStateEnum.READY, UUID.randomUUID(), passedCnt, 0, inputFlowId);
  }

  @Override
  protected FlowElement withoutLoopCharacteristics(Set<String> outgoing) {
    return new ReceiveTask(
        getId(), getId(), getIncoming(), outgoing, LoopCharacteristics.NONE, getMessageRef(), getIoMapping());
  }
}
