package nl.qunit.bpmnmeister.pi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.UUID;
import lombok.Getter;

@Getter
public class ProcessInstanceKey {
  public static final ProcessInstanceKey NONE =
      new ProcessInstanceKey(UUID.fromString("00000000-0000-0000-0000-000000000000"));

  private final UUID processInstanceId;

  @JsonCreator
  public ProcessInstanceKey(@JsonProperty("processInstanceId") @Nonnull UUID processInstanceId) {
    this.processInstanceId = processInstanceId;
  }

  @Override
  public String toString() {
    return "ProcessInstanceKey{" + "processInstanceId=" + processInstanceId + '}';
  }
}
