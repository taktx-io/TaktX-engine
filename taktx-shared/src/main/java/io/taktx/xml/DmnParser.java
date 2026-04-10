/*
 * TaktX - A high-performance BPMN engine
 * Copyright (c) 2025 Eric Hendriks
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at http://www.apache.org/licenses/LICENSE-2.0
 */
package io.taktx.xml;

import io.taktx.dto.DmnCollectOperator;
import io.taktx.dto.DmnDecisionDTO;
import io.taktx.dto.DmnDecisionTableDTO;
import io.taktx.dto.DmnDefinitionsKey;
import io.taktx.dto.DmnHitPolicy;
import io.taktx.dto.DmnInputClauseDTO;
import io.taktx.dto.DmnLiteralExpressionDTO;
import io.taktx.dto.DmnOutputClauseDTO;
import io.taktx.dto.DmnRuleDTO;
import io.taktx.dto.ParsedDmnDefinitionsDTO;
import io.taktx.util.SHA256;
import java.io.StringReader;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

public class DmnParser {

  public static final String DMN_NS = "https://www.omg.org/spec/DMN/20191111/MODEL/";

  private DmnParser() {}

  public static ParsedDmnDefinitionsDTO parse(String xml) {
    try {
      String hash = SHA256.getHash(xml);

      DocumentBuilderFactory dbf = DocumentBuilderFactory.newInstance();
      dbf.setNamespaceAware(true);
      DocumentBuilder db = dbf.newDocumentBuilder();
      Document doc = db.parse(new InputSource(new StringReader(xml)));
      Element root = doc.getDocumentElement();

      String definitionId = root.getAttribute("id");
      if (definitionId == null || definitionId.isEmpty()) {
        definitionId = root.getAttribute("namespace");
      }
      String name = root.getAttribute("name");

      List<DmnDecisionDTO> decisions = new ArrayList<>();
      NodeList decisionNodes = root.getElementsByTagNameNS(DMN_NS, "decision");
      for (int i = 0; i < decisionNodes.getLength(); i++) {
        Element decisionEl = (Element) decisionNodes.item(i);
        decisions.add(parseDecision(decisionEl));
      }

      DmnDefinitionsKey key = new DmnDefinitionsKey(definitionId, hash);
      return ParsedDmnDefinitionsDTO.builder()
          .definitionsKey(key)
          .name(name)
          .decisions(decisions)
          .build();
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 not available", e);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to parse DMN XML", e);
    }
  }

  private static DmnDecisionDTO parseDecision(Element decisionEl) {
    String id = decisionEl.getAttribute("id");
    String name = decisionEl.getAttribute("name");

    DmnDecisionTableDTO table = null;
    DmnLiteralExpressionDTO literal = null;

    NodeList tableNodes = decisionEl.getElementsByTagNameNS(DMN_NS, "decisionTable");
    if (tableNodes.getLength() > 0) {
      table = parseDecisionTable((Element) tableNodes.item(0));
    } else {
      NodeList literalNodes = decisionEl.getElementsByTagNameNS(DMN_NS, "literalExpression");
      if (literalNodes.getLength() > 0) {
        literal = parseLiteralExpression((Element) literalNodes.item(0));
      }
    }

    // Parse DRG information requirements (edges to required upstream decisions)
    List<String> requiredDecisionIds = new ArrayList<>();
    NodeList infoReqNodes = decisionEl.getElementsByTagNameNS(DMN_NS, "informationRequirement");
    for (int i = 0; i < infoReqNodes.getLength(); i++) {
      Element infoReqEl = (Element) infoReqNodes.item(i);
      NodeList reqDecNodes = infoReqEl.getElementsByTagNameNS(DMN_NS, "requiredDecision");
      for (int j = 0; j < reqDecNodes.getLength(); j++) {
        String href = ((Element) reqDecNodes.item(j)).getAttribute("href");
        // href is "#decisionId" — strip the leading #
        requiredDecisionIds.add(href.startsWith("#") ? href.substring(1) : href);
      }
    }

    return new DmnDecisionDTO(
        id, name, table, literal, requiredDecisionIds.isEmpty() ? null : requiredDecisionIds);
  }

  private static DmnDecisionTableDTO parseDecisionTable(Element tableEl) {
    String id = tableEl.getAttribute("id");

    String hitPolicyStr = tableEl.getAttribute("hitPolicy");
    // DMN 1.3 spec §8.2: default hit policy is UNIQUE when attribute is absent
    DmnHitPolicy hitPolicy =
        hitPolicyStr.isEmpty() ? DmnHitPolicy.UNIQUE : parseHitPolicy(hitPolicyStr);

    String aggregationStr = tableEl.getAttribute("aggregation");
    DmnCollectOperator collectOperator =
        aggregationStr.isEmpty() ? DmnCollectOperator.NONE : parseCollectOperator(aggregationStr);

    List<DmnInputClauseDTO> inputs = new ArrayList<>();
    List<DmnOutputClauseDTO> outputs = new ArrayList<>();
    List<DmnRuleDTO> rules = new ArrayList<>();

    NodeList children = tableEl.getChildNodes();
    for (int i = 0; i < children.getLength(); i++) {
      Node child = children.item(i);
      if (child.getNodeType() != Node.ELEMENT_NODE) continue;
      Element el = (Element) child;
      String localName = el.getLocalName();
      if ("input".equals(localName)) {
        inputs.add(parseInputClause(el));
      } else if ("output".equals(localName)) {
        outputs.add(parseOutputClause(el));
      } else if ("rule".equals(localName)) {
        rules.add(parseRule(el, inputs.size(), outputs.size()));
      }
    }

    return new DmnDecisionTableDTO(id, hitPolicy, collectOperator, inputs, outputs, rules);
  }

  private static DmnInputClauseDTO parseInputClause(Element inputEl) {
    String id = inputEl.getAttribute("id");
    String label = inputEl.getAttribute("label");
    String typeRef = "";
    String expression = "";

    NodeList exprNodes = inputEl.getElementsByTagNameNS(DMN_NS, "inputExpression");
    if (exprNodes.getLength() > 0) {
      Element exprEl = (Element) exprNodes.item(0);
      typeRef = exprEl.getAttribute("typeRef");
      NodeList textNodes = exprEl.getElementsByTagNameNS(DMN_NS, "text");
      if (textNodes.getLength() > 0) {
        expression = textNodes.item(0).getTextContent().trim();
      }
    }

    return new DmnInputClauseDTO(id, label, expression, typeRef);
  }

  private static DmnOutputClauseDTO parseOutputClause(Element outputEl) {
    String id = outputEl.getAttribute("id");
    String label = outputEl.getAttribute("label");
    String name = outputEl.getAttribute("name");
    String typeRef = outputEl.getAttribute("typeRef");
    return new DmnOutputClauseDTO(id, label, name, typeRef);
  }

  private static DmnRuleDTO parseRule(Element ruleEl, int inputCount, int outputCount) {
    String id = ruleEl.getAttribute("id");
    List<String> inputEntries = new ArrayList<>();
    List<String> outputEntries = new ArrayList<>();

    NodeList inputEntryNodes = ruleEl.getElementsByTagNameNS(DMN_NS, "inputEntry");
    for (int i = 0; i < inputEntryNodes.getLength(); i++) {
      Element entryEl = (Element) inputEntryNodes.item(i);
      NodeList textNodes = entryEl.getElementsByTagNameNS(DMN_NS, "text");
      String text = textNodes.getLength() > 0 ? textNodes.item(0).getTextContent().trim() : "";
      inputEntries.add(text);
    }

    NodeList outputEntryNodes = ruleEl.getElementsByTagNameNS(DMN_NS, "outputEntry");
    for (int i = 0; i < outputEntryNodes.getLength(); i++) {
      Element entryEl = (Element) outputEntryNodes.item(i);
      NodeList textNodes = entryEl.getElementsByTagNameNS(DMN_NS, "text");
      String text = textNodes.getLength() > 0 ? textNodes.item(0).getTextContent().trim() : "";
      outputEntries.add(text);
    }

    return new DmnRuleDTO(id, inputEntries, outputEntries);
  }

  private static DmnLiteralExpressionDTO parseLiteralExpression(Element litEl) {
    String id = litEl.getAttribute("id");
    String typeRef = litEl.getAttribute("typeRef");
    String expression = "";
    NodeList textNodes = litEl.getElementsByTagNameNS(DMN_NS, "text");
    if (textNodes.getLength() > 0) {
      expression = textNodes.item(0).getTextContent().trim();
    }
    return new DmnLiteralExpressionDTO(id, expression, typeRef);
  }

  private static DmnHitPolicy parseHitPolicy(String value) {
    // DMN 1.3 spec §8.2 uses uppercase names with spaces; underscore variants accepted for
    // convenience
    return switch (value.toUpperCase()) {
      case "UNIQUE" -> DmnHitPolicy.UNIQUE;
      case "FIRST" -> DmnHitPolicy.FIRST;
      case "ANY" -> DmnHitPolicy.ANY;
      case "COLLECT" -> DmnHitPolicy.COLLECT;
      case "RULE ORDER", "RULE_ORDER" -> DmnHitPolicy.RULE_ORDER;
      case "OUTPUT ORDER", "OUTPUT_ORDER" -> DmnHitPolicy.OUTPUT_ORDER;
      case "PRIORITY" -> DmnHitPolicy.PRIORITY;
      default -> throw new IllegalArgumentException("Unknown hit policy: " + value);
    };
  }

  private static DmnCollectOperator parseCollectOperator(String value) {
    return switch (value.toUpperCase()) {
      case "SUM" -> DmnCollectOperator.SUM;
      case "MIN" -> DmnCollectOperator.MIN;
      case "MAX" -> DmnCollectOperator.MAX;
      case "COUNT" -> DmnCollectOperator.COUNT;
      default -> DmnCollectOperator.NONE;
    };
  }
}
