/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.xml;

import io.taktx.bpmn.TDefinitions;
import io.taktx.dto.ParsedDefinitionsDTO;
import io.taktx.util.SHA256;
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
import lombok.Getter;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class BpmnParser {
  private BpmnParser() {
    // Static helper class
  }

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
      @SuppressWarnings("unchecked")
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
      throw new IllegalStateException(e);
    }
  }

  @Getter
  private static class MyHandler extends DefaultHandler {

    private final Map<String, String> namespaces = new HashMap<>();

    @Override
    public void startPrefixMapping(String prefix, String uri) {
      namespaces.put(prefix, uri);
    }
  }
}
