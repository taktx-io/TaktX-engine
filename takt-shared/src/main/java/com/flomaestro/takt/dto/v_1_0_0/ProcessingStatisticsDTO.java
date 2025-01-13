package com.flomaestro.takt.dto.v_1_0_0;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@EqualsAndHashCode
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class ProcessingStatisticsDTO {
  private int totalProcessInstancesStarted;
  private int totalProcessInstancesFinished;
  private int processInstancesStarted;
  private int processInstancesFinished;
  private int flowNodesStarted;
  private int flowNodesContinued;
  private int flowNodesFinished;
  private float averageRequestLatencyMs;
}
