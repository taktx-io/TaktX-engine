package io.taktx;

import lombok.Getter;

@Getter
public enum Topics {
  PROCESS_DEFINITION_STORE_TOPIC("process-definition-store"),
  PROCESS_DEFINITION_ACTIVATION_TOPIC("process-definition-activation"),
  MESSAGE_EVENT_TOPIC("message-event"),
  SCHEDULE_COMMANDS("schedule-commands"),
  INSTANCE_UPDATE_TOPIC("instance-update"),
  PROCESS_INSTANCE_TRIGGER_TOPIC("process-instance-trigger"),
  PROCESS_DEFINITIONS_TRIGGER_TOPIC("definitions-trigger");

  private final String topicName;

  Topics(String topicName) {
    this.topicName = topicName;
  }
}
