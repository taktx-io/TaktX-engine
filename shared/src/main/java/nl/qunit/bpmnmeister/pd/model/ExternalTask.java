package nl.qunit.bpmnmeister.pd.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.Map;
import java.util.Set;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import nl.qunit.bpmnmeister.pi.state.ExternalTaskState;

@Getter
@EqualsAndHashCode(callSuper = true)
public abstract class ExternalTask<S extends ExternalTaskState> extends Task<S> {

  private final String workerDefinition;
  private final String retries;
  private final String implementation;
  private final Map<String, String> headers;

  @JsonCreator
  protected ExternalTask(
      String id,
      String parentId,
      Set<String> incoming,
      Set<String> outgoing,
      LoopCharacteristics loopCharacteristics,
      InputOutputMapping ioMapping,
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
