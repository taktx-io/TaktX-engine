package nl.qunit.bpmnmeister.engine.pd;

import jakarta.xml.bind.JAXBException;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import javax.xml.parsers.ParserConfigurationException;
import nl.qunit.bpmnmeister.pd.model.Definitions;
import nl.qunit.bpmnmeister.pd.xml.BpmnParser;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.kstream.KeyValueMapper;
import org.xml.sax.SAXException;

public class DefinitionsMapper
    implements KeyValueMapper<String, String, KeyValue<String, Definitions>> {

  @Override
  public KeyValue<String, Definitions> apply(String key, String value) {
    try {
      Definitions parsed = new BpmnParser().parse(value);
      return KeyValue.pair(parsed.getDefinitionsKey().getProcessDefinitionId(), parsed);
    } catch (JAXBException
        | NoSuchAlgorithmException
        | IOException
        | ParserConfigurationException
        | SAXException e) {
      throw new IllegalStateException(e);
    }
  }
}
