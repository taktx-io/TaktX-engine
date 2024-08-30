package nl.qunit.bpmnmeister.engine.mapping;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

class MyMapperTest {

  @Test
  void testMap() {
    MyMapper mapper = Mappers.getMapper(MyMapper.class);
    SubClassADTO subClassADto = new SubClassADTO("name", "subNameA");
    BaseClass mapped = mapper.map(subClassADto);
    assertThat(mapped).isNotNull();

  }
}