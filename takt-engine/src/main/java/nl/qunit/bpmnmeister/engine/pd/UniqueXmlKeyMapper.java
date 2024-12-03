package nl.qunit.bpmnmeister.engine.pd;

import nl.qunit.bpmnmeister.pd.model.DefinitionsDTO;
import nl.qunit.bpmnmeister.pd.xml.BpmnParser;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.kstream.KeyValueMapper;

public class UniqueXmlKeyMapper
    implements KeyValueMapper<String, String, KeyValue<String, DefinitionsDTO>> {

  @Override
  public KeyValue<String, DefinitionsDTO> apply(String key, String value) {
      DefinitionsDTO parsed = new BpmnParser().parse(value);
      return KeyValue.pair(
          parsed.getDefinitionsKey().getProcessDefinitionId()
              + "-"
              + value.length()
              + "-"
              + parsed.getDefinitionsKey().getHash(),
          parsed);

  }
}
