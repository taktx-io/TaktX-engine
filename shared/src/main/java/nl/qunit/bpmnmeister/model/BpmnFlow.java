package nl.qunit.bpmnmeister.model;

import lombok.Data;

@Data
public class BpmnFlow {
    private String id;
    private BpmnElement target;
}
