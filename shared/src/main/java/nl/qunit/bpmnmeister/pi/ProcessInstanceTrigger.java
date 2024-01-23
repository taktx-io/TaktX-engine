package nl.qunit.bpmnmeister.pi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.util.UUID;

@Builder
@Getter
public class ProcessInstanceTrigger {
    private final UUID processInstanceId;
    private final String processDefinitionId;
    private final String generation;
    private final long version;
    private final String elementId;
    private final String inputFlowId;

    @JsonCreator
    public ProcessInstanceTrigger(
            @JsonProperty("processInstanceId") UUID processInstanceId,
            @JsonProperty("processDefinitionId") String processDefinitionId,
            @JsonProperty("generation") String generation,
            @JsonProperty("version") long version,
            @JsonProperty("elementId") String elementId,
            @JsonProperty("inputFlowId") String inputFlowId) {
        this.processInstanceId = processInstanceId;
        this.processDefinitionId = processDefinitionId;
        this.generation = generation;
        this.version = version;
        this.elementId = elementId;
        this.inputFlowId = inputFlowId;
    }
}