package nl.qunit.bpmnmeister.scheduler;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;
import java.util.UUID;
import nl.qunit.bpmnmeister.pi.StartCommand;

@JsonTypeInfo(use = Id.CLASS, property = "clazz")
@JsonSubTypes({
  @JsonSubTypes.Type(value = StartCommand.class),
})
public interface SchedulableMessage<T> {
  T getRecordKey(UUID rootInstanceKey);
}
