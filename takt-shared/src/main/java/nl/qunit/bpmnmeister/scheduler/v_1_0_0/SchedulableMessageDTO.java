package nl.qunit.bpmnmeister.scheduler.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import java.util.UUID;

@JsonTypeInfo(use = Id.CLASS, property = "clazz")
public interface SchedulableMessageDTO<T> {
  T getRecordKey(UUID processInstanceKey);
}
