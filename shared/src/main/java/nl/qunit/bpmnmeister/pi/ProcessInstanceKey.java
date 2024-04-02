package nl.qunit.bpmnmeister.pi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.Objects;
import java.util.UUID;
import lombok.Getter;

@Getter
public class ProcessInstanceKey {
  public static final ProcessInstanceKey NONE =
      new ProcessInstanceKey(UUID.fromString("00000000-0000-0000-0000-000000000000"));

  private final UUID processInstanceId;

  private final ProcessInstanceKey parentProcessInstanceId;

  @JsonCreator
  public ProcessInstanceKey(
      @JsonProperty("processInstanceId") @Nonnull UUID processInstanceId,
      @JsonProperty("parentProcessInstanceId") @Nonnull
          ProcessInstanceKey parentProcessInstanceId) {
    this.processInstanceId = processInstanceId;
    this.parentProcessInstanceId = parentProcessInstanceId;
  }

  public ProcessInstanceKey(@JsonProperty("processInstanceId") @Nonnull UUID processInstanceId) {
    this(processInstanceId, ProcessInstanceKey.NONE);
  }

  @Override
  public String toString() {
    return processInstanceId.toString();
  }

  @Override
  public boolean equals(Object o) {
    if (this == o) {
      return true;
    }
    if (o == null || getClass() != o.getClass()) {
      return false;
    }
    ProcessInstanceKey that = (ProcessInstanceKey) o;
    return Objects.equals(processInstanceId, that.processInstanceId);
  }

  @Override
  public int hashCode() {
    return Objects.hash(processInstanceId);
  }
}
