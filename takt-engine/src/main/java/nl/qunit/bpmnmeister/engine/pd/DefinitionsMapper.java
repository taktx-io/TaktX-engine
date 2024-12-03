package nl.qunit.bpmnmeister.engine.pd;

import nl.qunit.bpmnmeister.pd.model.DefinitionsDTO;
import nl.qunit.bpmnmeister.pd.model.DefinitionsKey;
import nl.qunit.bpmnmeister.pd.xml.BpmnParser;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.kstream.KeyValueMapper;

public class DefinitionsMapper
    implements KeyValueMapper<String, String, KeyValue<String, DefinitionsDTO>> {

  @Override
  public KeyValue<String, DefinitionsDTO> apply(String key, String value) {
      DefinitionsDTO parsed = new BpmnParser().parse(value);
      DefinitionsKey definitionsKey = parsed.getDefinitionsKey();
      String processDefinitionId = definitionsKey.getProcessDefinitionId();
      return KeyValue.pair(processDefinitionId, parsed);
  }
}
