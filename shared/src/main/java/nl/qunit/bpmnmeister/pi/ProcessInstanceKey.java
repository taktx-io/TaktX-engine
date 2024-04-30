package nl.qunit.bpmnmeister.pi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.UUID;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import nl.qunit.bpmnmeister.pd.model.Constants;

@Getter
@EqualsAndHashCode
public class ProcessInstanceKey {
  public static final ProcessInstanceKey NONE = new ProcessInstanceKey(Constants.NONE_UUID);

  private final UUID id;

  private final ProcessInstanceKey parentId;

  @JsonCreator
  public ProcessInstanceKey(
      @JsonProperty("id") @Nonnull UUID id,
      @JsonProperty("parentId") @Nonnull
      ProcessInstanceKey parentId) {
    this.id = id;
    this.parentId = parentId;
  }

  public ProcessInstanceKey(@JsonProperty("id") @Nonnull UUID id) {
    this(id, ProcessInstanceKey.NONE);
  }

}
