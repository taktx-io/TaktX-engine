package nl.qunit.bpmnmeister.model.processinstance;

import nl.qunit.bpmnmeister.model.processdefinition.BpmnElement;

public interface BpmnElementState {

  TriggerResult trigger(Trigger trigger, BpmnElement bpmnElement);
}
