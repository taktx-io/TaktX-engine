package io.taktx;

import lombok.Getter;

@Getter
public enum Topics {
  PROCESS_DEFINITION_ACTIVATION_TOPIC("process-definition-activation", "latest"),
  MESSAGE_EVENT_TOPIC("message-event", "latest"),
  SCHEDULE_COMMANDS("schedule-commands", "latest"),
  INSTANCE_UPDATE_TOPIC("instance-update", "latest"),
  PROCESS_INSTANCE_TRIGGER_TOPIC("process-instance", "latest"),
  EXTERNAL_TASK_META_TOPIC("external-task-meta", "earliest"),
  PROCESS_DEFINITIONS_TRIGGER_TOPIC("definitions", "latest");

  private final String topicName;
  private final String autoOffsetReset;

  Topics(String topicName, String autoOffsetReset) {
    this.topicName = topicName;
    this.autoOffsetReset = autoOffsetReset;
  }
}
