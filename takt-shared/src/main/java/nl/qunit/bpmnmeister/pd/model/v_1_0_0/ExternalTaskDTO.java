package nl.qunit.bpmnmeister.pd.model.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.Map;
import java.util.Set;
import lombok.EqualsAndHashCode;
import lombok.Getter;

@Getter
@EqualsAndHashCode(callSuper = true)
public abstract class ExternalTaskDTO extends TaskDTO {

  private final String workerDefinition;
  private final String retries;
  private final String implementation;
  private final Map<String, String> headers;

  @JsonCreator
  protected ExternalTaskDTO(
      String id,
      String parentId,
      Set<String> incoming,
      Set<String> outgoing,
      LoopCharacteristicsDTO loopCharacteristics,
      InputOutputMappingDTO ioMapping,
      String workerDefinition,
      String retries,
      String implementation,
      Map<String, String> headers) {
    super(id, parentId, incoming, outgoing, loopCharacteristics, ioMapping);
    this.workerDefinition = workerDefinition;
    this.retries = retries;
    this.implementation = implementation;
    this.headers = headers;
  }
}
