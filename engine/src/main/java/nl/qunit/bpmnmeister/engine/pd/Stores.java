package nl.qunit.bpmnmeister.engine.pd;

public class Stores {

  private Stores() {
    // prevent instantiation
  }

  public static final String SCHEDULES_STORE_NAME = "schedule-store";
  public static final String PROCESS_INSTANCE_STORE_NAME = "process-instance-store";
  public static final String PROCESS_INSTANCE_DEFINITION_STORE_NAME =
      "process-instance-definition-store";
  public static final String DEFINITION_COUNT_BY_ID_STORE_NAME = "definition-count-by-id-store";
  public static final String XML_BY_HASH_STORE_NAME = "xml-by-hash-store";
  public static final String PROCESS_DEFINITION_STORE_NAME = "process-definition-store";
  public static final String MESSAGE_SUBSCRIPTION_STORE_NAME = "message-subscription-store";
  public static final String CHILD_PARENT_PROCESS_INSTANCE_KEY_STORE_NAME =
      "child-to-parent-instance-key-store";
}
