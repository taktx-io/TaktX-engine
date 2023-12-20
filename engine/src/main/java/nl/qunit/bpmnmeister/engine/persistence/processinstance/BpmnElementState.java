package nl.qunit.bpmnmeister.engine.persistence.processinstance;

import nl.qunit.bpmnmeister.engine.persistence.processdefinition.BpmnElement;

public interface BpmnElementState {

  TriggerResult trigger(Trigger trigger, BpmnElement bpmnElement);
}
