package nl.qunit.bpmnmeister.pi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;
import nl.qunit.bpmnmeister.pd.model.Constants;

@Getter
@EqualsAndHashCode
@ToString
public class ProcessInstanceKey {
  public static final ProcessInstanceKey NONE = new ProcessInstanceKey(Constants.NONE_UUID);

  private final UUID id;

  @JsonCreator
  public ProcessInstanceKey(@JsonProperty("id") UUID id) {
    this.id = id;
  }
}
