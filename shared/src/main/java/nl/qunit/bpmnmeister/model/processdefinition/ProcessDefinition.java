package nl.qunit.bpmnmeister.model.processdefinition;

import java.util.Map;
import lombok.Builder;

@Builder
public record ProcessDefinition(
    String id, Map<String, BpmnElement> bpmnElements, Map<String, BpmnFlow> flows) {}
