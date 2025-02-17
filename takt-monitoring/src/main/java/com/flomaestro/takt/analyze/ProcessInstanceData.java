package com.flomaestro.takt.analyze;

import com.influxdb.annotations.Column;
import com.influxdb.annotations.Measurement;
import java.time.Instant;
import lombok.Getter;
import lombok.Setter;

@Measurement(name = "processInstanceData")
@Getter
@Setter
public class ProcessInstanceData {
  @Column(timestamp = true)
  private Instant time;

  @Column private String definitionId;
  @Column private long averageProcessingTime;
}
