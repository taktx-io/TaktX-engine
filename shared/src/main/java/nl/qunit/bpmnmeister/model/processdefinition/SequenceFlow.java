package nl.qunit.bpmnmeister.model.processdefinition;

import java.util.function.Predicate;
import nl.qunit.bpmnmeister.model.processinstance.ProcessInstance;

public record SequenceFlow(String id, String target, Predicate<ProcessInstance> condition) {}
