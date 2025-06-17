package io.taktx.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonFormat.Shape;
import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.Map;
import java.util.Set;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode
@JsonFormat(shape = Shape.ARRAY)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class FlowNodeInstancesDTO {
  private ProcessInstanceState state;

  private int activeCnt;

  private long elementInstanceCnt;

  private Map<String, Long> gatewayInstances;

  private Map<String, Set<String>> messageSubscriptions;

  private Set<InstanceScheduleKeyDTO> scheduleKeys;

  public FlowNodeInstancesDTO(
      ProcessInstanceState state,
      int activeCnt,
      long elementInstanceCnt,
      Map<String, Long> gatewayInstances,
      Map<String, Set<String>> messageSubscriptions,
      Set<InstanceScheduleKeyDTO> scheduleKeys) {
    this.state = state;
    this.activeCnt = activeCnt;
    this.elementInstanceCnt = elementInstanceCnt;
    this.gatewayInstances = gatewayInstances;
    this.messageSubscriptions = messageSubscriptions;
    this.scheduleKeys = scheduleKeys;
  }
}
