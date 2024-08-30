package nl.qunit.bpmnmeister.pi;

import java.util.UUID;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ExternalTaskInfo {

  private final String externalTaskId;
  private final String elementId;
  private final UUID elementInstanceId;
  private final Variables2 variables;
  private final String startTime;
}
