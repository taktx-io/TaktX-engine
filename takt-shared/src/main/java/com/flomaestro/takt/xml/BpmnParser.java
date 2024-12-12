package com.flomaestro.takt.xml;

import com.flomaestro.bpmn.TDefinitions;
import com.flomaestro.takt.dto.v_1_0_0.ParsedDefinitionsDTO;
import com.flomaestro.takt.util.SHA256;
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
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class BpmnParser {

  public static ParsedDefinitionsDTO parse(String xml) {
    try {
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
    } catch (JAXBException
        | ParserConfigurationException
        | SAXException
        | NoSuchAlgorithmException
        | IOException e) {
      throw new RuntimeException(e);
    }
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
