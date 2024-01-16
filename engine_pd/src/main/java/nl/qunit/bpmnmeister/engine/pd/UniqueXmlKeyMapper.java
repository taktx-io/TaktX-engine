package nl.qunit.bpmnmeister.engine.pd;

import jakarta.xml.bind.JAXBException;
import java.security.NoSuchAlgorithmException;
import lombok.RequiredArgsConstructor;
import nl.qunit.bpmnmeister.pd.model.Definitions;
import nl.qunit.bpmnmeister.pd.xml.BpmnParser;
import nl.qunit.bpmnmeister.util.GenerationExtractor;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.kstream.KeyValueMapper;

@RequiredArgsConstructor
public class UniqueXmlKeyMapper
    implements KeyValueMapper<String, String, KeyValue<String, Definitions>> {
  private final GenerationExtractor generationExtractor;
  private final BpmnParser bpmnParser;

  @Override
  public KeyValue<String, Definitions> apply(String key, String value) {
    try {
      String generation = generationExtractor.getGenerationFromString(key).orElseThrow();
      Definitions parsed = bpmnParser.parse(value, generation);
      return KeyValue.pair(
          parsed.getProcessDefinitionId()
              + "-"
              + generation
              + "-"
              + value.length()
              + "-"
              + parsed.getHash(),
          parsed);
    } catch (JAXBException e) {
      throw new RuntimeException(e);
    } catch (NoSuchAlgorithmException e) {
      throw new RuntimeException(e);
    }
  }
}
