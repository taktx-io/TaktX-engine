package nl.qunit.bpmnmeister.engine.persistence.processdefinition;

public record SequenceFlow(String id, String target, String condition) {}
