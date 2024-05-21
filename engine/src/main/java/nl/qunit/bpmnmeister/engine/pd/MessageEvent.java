package nl.qunit.bpmnmeister.engine.pd;

import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonTypeInfo.Id;

@JsonTypeInfo(use = Id.CLASS, property = "clazz")
@JsonSubTypes({
  @JsonSubTypes.Type(value = MessageEventImpl.class),
  @JsonSubTypes.Type(value = MessageSubscription.class)
})
public interface MessageEvent {}
