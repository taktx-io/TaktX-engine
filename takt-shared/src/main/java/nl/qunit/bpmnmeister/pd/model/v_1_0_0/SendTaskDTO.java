package nl.qunit.bpmnmeister.pd.model.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.Map;
import java.util.Set;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode(callSuper = true)
public class SendTaskDTO extends ExternalTaskDTO {
  @JsonCreator
  public SendTaskDTO(
      @Nonnull @JsonProperty("id") String id,
      @Nonnull @JsonProperty("parentId") String parentId,
      @Nonnull @JsonProperty("workerDefinition") String workerDefinition,
      @Nonnull @JsonProperty("retries") String retries,
      @Nonnull @JsonProperty("incoming") Set<String> incoming,
      @Nonnull @JsonProperty("outgoing") Set<String> outgoing,
      @Nonnull @JsonProperty("implementation") String implementation,
      @Nonnull @JsonProperty("loopCharacteristics") LoopCharacteristicsDTO loopCharacteristics,
      @Nonnull @JsonProperty("headers") Map<String, String> headers,
      @Nonnull @JsonProperty("ioMapping") InputOutputMappingDTO ioMapping) {
    super(
        id,
        parentId,
        incoming,
        outgoing,
        loopCharacteristics,
        ioMapping,
        workerDefinition,
        retries,
        implementation,
        headers);
  }
}
