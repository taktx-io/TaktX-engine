package nl.qunit.bpmnmeister;

import lombok.Getter;

@Getter
public enum Topics {
  PROCESS_DEFINITION_PARSED_TOPIC("process-definition-parsed-topic"),
  PROCESS_DEFINTIION_ACTIVATION_TOPIC("process-definition-activation-topic"),
  MESSAGE_EVENT_TOPIC("message-event-topic"),
  SCHEDULE_COMMANDS("schedule-commands"),
  INSTANCE_UPDATE_TOPIC("instance-update-topic"),
  PROCESS_INSTANCE_TRIGGER_TOPIC("process-instance-trigger-topic"),
  EXTERNAL_TASK_TRIGGER_TOPIC("external-task-trigger-topic"),
  PROCESS_DEFINITIONS_TRIGGER_TOPIC("definitions-trigger-topic");

  private final String topicName;

  Topics(String topicName) {
    this.topicName = topicName;
  }
}
