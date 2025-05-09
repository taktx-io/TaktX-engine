package io.taktx.dto;

import java.time.LocalDateTime;
import java.util.Map;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TopicRegistration {
  private String topicName; // The full topic name (with prefix if applicable)
  private boolean fixedTopic; // Indicates if this is a fixed system topic
  private int partitions;
  private short replicationFactor;
  private TopicStatus status;
  private Map<String, String> configs;
  private LocalDateTime requestedTime;
  private LocalDateTime createdTime;
  private String errorMessage;
}
