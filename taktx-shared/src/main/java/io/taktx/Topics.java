package io.taktx;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;

@Getter
public enum Topics {
  TOPIC_META_TOPIC("topic-meta", true, CleanupPolicy.DELETE),
  PROCESS_DEFINITION_ACTIVATION_TOPIC("process-definition-activation", false, CleanupPolicy.DELETE),
  MESSAGE_EVENT_TOPIC("message-event", false, CleanupPolicy.DELETE),
  SCHEDULE_COMMANDS("schedule-commands", false, CleanupPolicy.DELETE),
  INSTANCE_UPDATE_TOPIC("instance-update", false, CleanupPolicy.DELETE),
  PROCESS_INSTANCE_TRIGGER_TOPIC("process-instance", false, CleanupPolicy.DELETE),
  PROCESS_DEFINITIONS_TRIGGER_TOPIC("definitions", false, CleanupPolicy.DELETE),
  USER_TASK_TRIGGER_TOPIC("usertasks", false, CleanupPolicy.COMPACT),
  USER_TASK_RESPONSE_TOPIC("usertasks-response", false, CleanupPolicy.DELETE);

  private final String topicName;
  private final boolean initialAvailable;
  private final CleanupPolicy cleanupPolicy;

  Topics(String topicName, boolean initialAvailable, CleanupPolicy cleanupPolicy) {
    this.topicName = topicName;
    this.initialAvailable = initialAvailable;
    this.cleanupPolicy = cleanupPolicy;
  }

  public static Set<Topics> initialFixedTopics() {
    return Arrays.stream(values()).filter(t -> t.initialAvailable).collect(Collectors.toSet());
  }

  public static Set<Topics> managedFixedTopics() {
    return Arrays.stream(values()).filter(t -> !t.initialAvailable).collect(Collectors.toSet());
  }
}
