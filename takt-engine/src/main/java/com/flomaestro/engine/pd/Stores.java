package com.flomaestro.engine.pd;

import lombok.Getter;

@Getter
public enum Stores {
  SCHEDULES("schedules"),
  PROCESS_INSTANCE("pi"),
  FLOW_NODE_INSTANCE("fni"),
  PROCESS_INSTANCE_DEFINITION("pid"),
  DEFINITION_COUNT_BY_ID("d-count-by-id"),
  XML_BY_HASH("xml-by-hash"),
  PROCESS_DEFINITION("pd"),
  DEFINITION_MESSAGE_SUBSCRIPTION("pd-message-subscription"),
  CORRELATION_MESSAGE_SUBSCRIPTION("correlation-subscription"),
  VARIABLES("vars-store"),
  VERSION_BY_HASH("vre-by-hash");

  private final String storename;

  Stores(String storename) {
    this.storename = storename;
  }
}
