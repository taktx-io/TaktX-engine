package nl.qunit.bpmnmeister.engine.pd;

import jakarta.xml.bind.JAXBException;
import java.security.NoSuchAlgorithmException;
import nl.qunit.bpmnmeister.pd.model.Definitions;
import nl.qunit.bpmnmeister.pd.xml.BpmnParser;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.kstream.KeyValueMapper;

public class UniqueXmlKeyMapper
    implements KeyValueMapper<String, String, KeyValue<String, Definitions>> {

  @Override
  public KeyValue<String, Definitions> apply(String key, String value) {
    try {
      Definitions parsed = BpmnParser.parse(value);
      return KeyValue.pair(
          parsed.getDefinitionsKey().getProcessDefinitionId()
              + "-"
              + value.length()
              + "-"
              + parsed.getDefinitionsKey().getHash(),
          parsed);
    } catch (JAXBException e) {
      throw new RuntimeException(e);
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
  }
}
