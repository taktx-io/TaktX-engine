package nl.qunit.bpmnmeister;

public enum Topics {
  PROCESS_DEFINITION_PARSED_TOPIC("process-definition-parsed-topic"),
  PROCESS_DEFINTIION_ACTIVATION_TOPIC("process-definition-activation-topic"),
  PROCESS_INSTANCE_START_COMMAND_TOPIC("process-instance-start-command-topic"),
  XML_TOPIC("process-definition-xml-topic"),
  SCHEDULE_COMMANDS("schedule-commands"),
  PROCESS_INSTANCE_TRIGGER_TOPIC("process-instance-trigger-topic"),
  EXTERNAL_TASK_TRIGGER_TOPIC("external-task-trigger-topic");

  private final String topicName;

  Topics(String topicName) {
    this.topicName = topicName;
  }

  public String getTopicName() {
    return topicName;
  }

  @Override
  public String toString() {
    return super.toString();
  }
}
