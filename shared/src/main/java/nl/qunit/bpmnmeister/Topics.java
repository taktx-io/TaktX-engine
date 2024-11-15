package nl.qunit.bpmnmeister;

public enum Topics {
  PROCESS_DEFINITION_PARSED_TOPIC("process-definition-parsed-topic"),
  PROCESS_DEFINTIION_ACTIVATION_TOPIC("process-definition-activation-topic"),
  XML_TOPIC("process-definition-xml-topic"),
  MESSAGE_EVENT_TOPIC("message-event-topic"),
  SCHEDULE_COMMANDS("schedule-commands"),
  PROCESS_INSTANCE_UPDATE_TOPIC("process-instance-update-topic"),
  PROCESS_INSTANCE_TRIGGER_TOPIC("process-instance-trigger-topic"),
  EXTERNAL_TASK_TRIGGER_TOPIC("external-task-trigger-topic"),
  PROCESS_INSTANCE_MIGRATION_TOPIC("process-migration-topic"),
  PROCESS_DEFINITIONS_TOPIC("definitions-topic");

  private final String topicName;

  Topics(String topicName) {
    this.topicName = topicName;
  }

  public String getTopicName() {
    return topicName;
  }
}
