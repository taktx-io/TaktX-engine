package nl.qunit.bpmnmeister.engine;

import nl.qunit.bpmnmeister.model.processinstance.Trigger;

public interface Processor<T> {
  T trigger(T elementInfo, Trigger trigger);
}
