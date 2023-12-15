package nl.qunit.bpmnmeister.model;

import lombok.Data;

import java.util.List;
import java.util.concurrent.Flow;

@Data
public class StartEvent implements BpmnElement {
    private String id;
    private List<BpmnFlow> outputFlows;
}
