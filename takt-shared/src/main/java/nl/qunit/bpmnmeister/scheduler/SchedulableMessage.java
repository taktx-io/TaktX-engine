package nl.qunit.bpmnmeister.scheduler;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import java.util.UUID;

@JsonTypeInfo(use = Id.CLASS, property = "clazz")
public interface SchedulableMessage<T> {
  T getRecordKey(UUID processInstanceKey);
}
