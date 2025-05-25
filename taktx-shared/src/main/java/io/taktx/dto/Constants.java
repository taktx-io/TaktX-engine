package io.taktx.dto;

import java.util.UUID;

public class Constants {
  private Constants() {
    // Prevent instantiations
  }

  public static final String EXTERNAL_TASK_TRIGGER_TOPIC_PREFIX = "external-task-trigger-";
  public static final Long MIN_LONG = 0x00000000L;
  public static final Long MAX_LONG = 0xffffffffL;
  public static final UUID MIN_UUID = new UUID(0, 0);
  public static final UUID MAX_UUID = new UUID(0xFFFFFFFFL, 0xFFFFFFFFFFFFFFFFL);
}
