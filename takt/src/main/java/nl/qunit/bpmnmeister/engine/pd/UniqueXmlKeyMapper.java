package nl.qunit.bpmnmeister.engine.pd;

import jakarta.xml.bind.JAXBException;
import java.io.IOException;
import java.security.NoSuchAlgorithmException;
import javax.xml.parsers.ParserConfigurationException;
import nl.qunit.bpmnmeister.pd.model.DefinitionsDTO;
import nl.qunit.bpmnmeister.pd.xml.BpmnParser;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.kstream.KeyValueMapper;
import org.xml.sax.SAXException;

public class UniqueXmlKeyMapper
    implements KeyValueMapper<String, String, KeyValue<String, DefinitionsDTO>> {

  @Override
  public KeyValue<String, DefinitionsDTO> apply(String key, String value) {
    try {
      DefinitionsDTO parsed = new BpmnParser().parse(value);
      return KeyValue.pair(
          parsed.getDefinitionsKey().getProcessDefinitionId()
              + "-"
              + value.length()
              + "-"
              + parsed.getDefinitionsKey().getHash(),
          parsed);
    } catch (JAXBException
        | NoSuchAlgorithmException
        | IOException
        | ParserConfigurationException
        | SAXException e) {
      throw new IllegalStateException(e);
    }
  }
}
