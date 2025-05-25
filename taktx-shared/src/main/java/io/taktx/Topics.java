package io.taktx;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.Getter;

@Getter
public enum Topics {
  TOPIC_META_TOPIC("topic-meta", true),
  PROCESS_DEFINITION_ACTIVATION_TOPIC("process-definition-activation", false),
  MESSAGE_EVENT_TOPIC("message-event", false),
  SCHEDULE_COMMANDS("schedule-commands", false),
  INSTANCE_UPDATE_TOPIC("instance-update", false),
  PROCESS_INSTANCE_TRIGGER_TOPIC("process-instance", false),
  PROCESS_DEFINITIONS_TRIGGER_TOPIC("definitions", false);

  private final String topicName;
  private final boolean initialAvailable;

  Topics(String topicName, boolean initialAvailable) {
    this.topicName = topicName;
    this.initialAvailable = initialAvailable;
  }

  public static Set<Topics> initialFixedTopics() {
    return Arrays.stream(values()).filter(t -> t.initialAvailable).collect(Collectors.toSet());
  }

  public static Set<Topics> managedFixedTopics() {
    return Arrays.stream(values()).filter(t -> !t.initialAvailable).collect(Collectors.toSet());
  }
}
