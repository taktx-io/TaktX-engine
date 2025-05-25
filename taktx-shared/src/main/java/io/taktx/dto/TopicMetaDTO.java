package io.taktx.dto;

import io.taktx.CleanupPolicy;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Data
@EqualsAndHashCode
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class TopicMetaDTO {
  private String topicName; // The actual Kafka topic name
  private int nrPartitions;
  private CleanupPolicy cleanupPolicy;
}
