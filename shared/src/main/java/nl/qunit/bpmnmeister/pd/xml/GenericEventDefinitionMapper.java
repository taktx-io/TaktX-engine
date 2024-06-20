package nl.qunit.bpmnmeister.pd.xml;

import jakarta.xml.bind.JAXBElement;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import nl.qunit.bpmnmeister.bpmn.TEventDefinition;
import nl.qunit.bpmnmeister.bpmn.TLinkEventDefinition;
import nl.qunit.bpmnmeister.bpmn.TMessageEventDefinition;
import nl.qunit.bpmnmeister.bpmn.TTimerEventDefinition;
import nl.qunit.bpmnmeister.pd.model.EventDefinition;
import nl.qunit.bpmnmeister.pd.model.LinkEventDefinition;
import nl.qunit.bpmnmeister.pd.model.MessageEventDefinition;
import nl.qunit.bpmnmeister.pd.model.TimerEventDefinition;

public class GenericEventDefinitionMapper implements EventDefinitionMapper {

  public Set<EventDefinition> map(
      List<JAXBElement<? extends TEventDefinition>> eventDefinition, String parentId) {
    return eventDefinition.stream()
        .map(JAXBElement::getValue)
        .map(ed -> mapEventDefinition(ed, parentId))
        .collect(Collectors.toSet());
  }

  private EventDefinition mapEventDefinition(TEventDefinition ed, String parentId) {
    if (ed instanceof TTimerEventDefinition timerEventDefinition) {
      return mapTimerEventDefinition(parentId, timerEventDefinition);
    } else if (ed instanceof TMessageEventDefinition messageEventDefinition) {
      return mapMessageEventDefinition(messageEventDefinition);
    } else if (ed instanceof TLinkEventDefinition linkEventDefinition) {
      return mapLinkEventDefinition(linkEventDefinition);
    }
    throw new IllegalStateException("Unknown event definition: " + ed.getClass().getName());
  }

  private EventDefinition mapLinkEventDefinition(TLinkEventDefinition linkEventDefinition) {
    return new LinkEventDefinition(
        linkEventDefinition.getId(), linkEventDefinition.getName());
  }

  private static MessageEventDefinition mapMessageEventDefinition(
      TMessageEventDefinition messageEventDefinition) {
    return new MessageEventDefinition(
        messageEventDefinition.getId(), messageEventDefinition.getMessageRef().getLocalPart());
  }

  private TimerEventDefinition mapTimerEventDefinition(
      String parentId, TTimerEventDefinition timerEventDefinition) {
    String duration =
        timerEventDefinition.getTimeDuration() != null
            ? timerEventDefinition.getTimeDuration().getContent().stream()
                .map(Object::toString)
                .collect(Collectors.joining(""))
            : "";
    String cycle =
        timerEventDefinition.getTimeCycle() != null
            ? timerEventDefinition.getTimeCycle().getContent().stream()
                .map(Object::toString)
                .collect(Collectors.joining(""))
            : "";
    String timeDate =
        timerEventDefinition.getTimeDate() != null
            ? timerEventDefinition.getTimeDate().getContent().stream()
                .map(Object::toString)
                .collect(Collectors.joining(""))
            : "";
    return new TimerEventDefinition(
        timerEventDefinition.getId(), parentId, timeDate, duration, cycle);
  }
}
