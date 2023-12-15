package nl.qunit.bpmnmeister.model;

import lombok.Data;

import java.util.List;

@Data
public class ProcessDefinition {
    private String id;
    private String name;
    private List<StartEvent> startEvents;
    
}
