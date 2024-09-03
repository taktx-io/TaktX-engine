package nl.qunit.bpmnmeister.engine.pi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Set;
import nl.qunit.bpmnmeister.pd.model.FlowElement2;
import nl.qunit.bpmnmeister.pd.model.InputOutputMappingDTO;
import nl.qunit.bpmnmeister.pd.model.IoVariableMappingDTO;
import nl.qunit.bpmnmeister.pd.model.LinkEventDefinitionDTO;
import nl.qunit.bpmnmeister.pd.model.StartEvent2;
import nl.qunit.bpmnmeister.pd.model.StartEventDTO;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class DtoMapperTest
{
    @Test
    void testMap()
    {
        DtoMapper dtoMapper = Mappers.getMapper(DtoMapper.class);
        LinkEventDefinitionDTO eventDefinition = new LinkEventDefinitionDTO("eventDefinitionId", "name");
        InputOutputMappingDTO ioMapping = new InputOutputMappingDTO(Set.of(new IoVariableMappingDTO("inputSource", "inputTarget")), Set.of(new IoVariableMappingDTO("outputSource", "outputTarget")));
        StartEventDTO startEventDTO = new StartEventDTO("id", "parentId", Set.of("incoming"), Set.of("outgoing"), Set.of(eventDefinition), ioMapping);
        FlowElement2 map = dtoMapper.getFlowElement(startEventDTO);

        assertNotNull(map);
        assertThat(map).isInstanceOf(StartEvent2.class);
        StartEvent2 startEvent2 = (StartEvent2) map;

        assertThat(startEvent2.getId()).isEqualTo("id");
        assertThat(startEvent2.getIncoming()).containsExactly("incoming");
        assertThat(startEvent2.getOutgoing()).containsExactly("outgoing");
        assertThat(startEvent2.getEventDefinitions().iterator().next().getId()).isEqualTo("eventDefinitionId");
    }

}