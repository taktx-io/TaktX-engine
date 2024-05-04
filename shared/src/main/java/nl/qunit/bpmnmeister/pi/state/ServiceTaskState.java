package nl.qunit.bpmnmeister.pi.state;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import java.util.UUID;
import lombok.Getter;

@Getter
public class ServiceTaskState extends TaskState {

  private final int attempt;

  @JsonCreator
  public ServiceTaskState(
      @Nonnull @JsonProperty("state") ActivityStateEnum state,
      @Nonnull @JsonProperty("elementInstanceId") UUID elementInstanceId,
      @JsonProperty("passedCnt") int passedCnt,
      @JsonProperty("attempt") int attempt) {
    super(state, elementInstanceId, passedCnt);
    this.attempt = attempt;
  }

}
