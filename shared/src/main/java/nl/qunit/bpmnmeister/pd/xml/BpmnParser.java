package nl.qunit.bpmnmeister.pd.xml;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import java.io.StringReader;
import java.security.NoSuchAlgorithmException;
import nl.qunit.bpmnmeister.bpmn.TDefinitions;
import nl.qunit.bpmnmeister.pd.model.Definitions;
import nl.qunit.bpmnmeister.util.SHA256;

public class BpmnParser {

  public static Definitions parse(String xml, Integer generation)
      throws JAXBException, NoSuchAlgorithmException {
    JAXBContext context = JAXBContext.newInstance(TDefinitions.class);
    Unmarshaller un = context.createUnmarshaller();
    JAXBElement<TDefinitions> definitions =
        (JAXBElement<TDefinitions>) un.unmarshal(new StringReader(xml));
    String hash = SHA256.getHash(xml);
    return BpmnMapper.map(definitions.getValue(), hash, generation);
  }
}
