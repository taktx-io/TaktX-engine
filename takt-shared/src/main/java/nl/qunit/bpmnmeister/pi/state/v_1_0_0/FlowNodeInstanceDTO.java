package nl.qunit.bpmnmeister.pi.state.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.SuperBuilder;

@Getter
@JsonTypeInfo(use = Id.CLASS, property = "clazz")
@ToString
@SuperBuilder(toBuilder = true)
public abstract class FlowNodeInstanceDTO {
  private final UUID elementInstanceId;
  @Setter private UUID parentElementInstanceId;
  @Setter private String elementId;

  private final int passedCnt;

  protected FlowNodeInstanceDTO(UUID elementInstanceId, String elementId, int passedCnt) {
    this.elementInstanceId = elementInstanceId;
    this.elementId = elementId;
    this.passedCnt = passedCnt;
  }

  @JsonIgnore
  public boolean isTerminated() {
    return false;
  }

  @JsonIgnore
  public boolean isFailed() {
    return false;
  }
}
