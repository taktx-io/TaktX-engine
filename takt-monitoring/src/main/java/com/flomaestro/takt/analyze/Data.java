package com.flomaestro.takt.analyze;

import com.influxdb.annotations.Column;
import com.influxdb.annotations.Measurement;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Measurement(name = "data")
@Getter
@Setter
public class Data {
  @Column(timestamp = true)
  private Instant time;

  @Column private int processInstancesStarted;
  @Column private int processInstancesFinished;
  @Column private int flowNodesStarted;
  @Column private int flowNodesContinued;
  @Column private int flowNodesFinished;
  @Column private float averageRequestLatencyMs;
}
