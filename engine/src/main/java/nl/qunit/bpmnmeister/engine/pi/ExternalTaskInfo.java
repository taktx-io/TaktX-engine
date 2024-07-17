package nl.qunit.bpmnmeister.engine.pi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;
import lombok.Getter;
import lombok.ToString;
import nl.qunit.bpmnmeister.pi.Variables;

@Getter
@ToString
public class ExternalTaskInfo {

  private final String externalTaskId;
  private final String elementId;
  private final UUID elementInstanceId;
  private final Variables variables;

  @JsonCreator
  public ExternalTaskInfo(
      @JsonProperty("externalTaskId") String externalTaskId,
      @JsonProperty("elementId") String elementId,
      @JsonProperty("elementInstanceId") UUID elementInstanceId,
      @JsonProperty("variables") Variables variables) {
    this.externalTaskId = externalTaskId;
    this.elementId = elementId;
    this.elementInstanceId = elementInstanceId;
    this.variables = variables;
  }
}
