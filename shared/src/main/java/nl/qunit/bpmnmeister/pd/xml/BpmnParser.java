package nl.qunit.bpmnmeister.pd.xml;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import java.io.StringReader;
import java.security.NoSuchAlgorithmException;
import lombok.RequiredArgsConstructor;
import nl.qunit.bpmnmeister.bpmn.TDefinitions;
import nl.qunit.bpmnmeister.pd.model.Definitions;
import nl.qunit.bpmnmeister.util.SHA256;

@ApplicationScoped
@RequiredArgsConstructor
public class BpmnParser {
  private final BpmnMapper bpmnMapper;
  private final SHA256 sha256;

  public Definitions parse(String xml) throws JAXBException, NoSuchAlgorithmException {
    JAXBContext context = JAXBContext.newInstance(TDefinitions.class);
    Unmarshaller un = context.createUnmarshaller();
    Object unmarshal = un.unmarshal(new StringReader(xml));
    JAXBElement<TDefinitions> definitions = (JAXBElement<TDefinitions>) unmarshal;
    String hash = sha256.getHash(xml);
    return bpmnMapper.map(definitions.getValue(), hash);
  }
}
