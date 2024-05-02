package nl.qunit.bpmnmeister.pd.xml;

import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBElement;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import java.io.IOException;
import java.io.StringReader;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import nl.qunit.bpmnmeister.bpmn.TDefinitions;
import nl.qunit.bpmnmeister.pd.model.Definitions;
import nl.qunit.bpmnmeister.util.SHA256;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class BpmnParser {

  public Definitions parse(String xml)
      throws JAXBException,
          NoSuchAlgorithmException,
          SAXException,
          IOException,
          ParserConfigurationException {
    JAXBContext context = JAXBContext.newInstance(TDefinitions.class);

    MyHandler handler = new MyHandler();
    SAXParserFactory saxParserFactory = SAXParserFactory.newInstance();
    saxParserFactory.setNamespaceAware(true);
    SAXParser saxParser = saxParserFactory.newSAXParser();
    saxParser.parse(new InputSource(new StringReader(xml)), handler);
    Set<String> namespaces = new HashSet<>(handler.getNamespaces().values());

    Unmarshaller un = context.createUnmarshaller();
    JAXBElement<TDefinitions> definitions =
        (JAXBElement<TDefinitions>) un.unmarshal(new StringReader(xml));
    String hash = SHA256.getHash(xml);
    BpmnMapper mapper = new BpmnMapperFactory(namespaces).createBpmnMapper();
    return mapper.map(definitions.getValue(), hash);
  }

  private static class MyHandler extends DefaultHandler {

    private final Map<String, String> namespaces = new HashMap<>();

    @Override
    public void startPrefixMapping(String prefix, String uri) throws SAXException {
      namespaces.put(prefix, uri);
    }

    public Map<String, String> getNamespaces() {
      return namespaces;
    }
  }
}
