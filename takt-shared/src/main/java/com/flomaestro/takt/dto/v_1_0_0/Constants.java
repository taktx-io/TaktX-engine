package com.flomaestro.takt.dto.v_1_0_0;

import java.util.UUID;

public class Constants {
  public static final String NONE = "_";
  public static final UUID NONE_UUID = UUID.fromString("00000000-0000-0000-0000-000000000000");
  public static final UUID MIN_UUID = new UUID(0, 0);
  public static final UUID MAX_UUID = new UUID(0xFFFFFFFFFFFFFFFFL, 0xFFFFFFFFFFFFFFFFL);
}
