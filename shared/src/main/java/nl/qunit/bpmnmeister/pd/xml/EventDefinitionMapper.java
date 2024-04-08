package nl.qunit.bpmnmeister.pd.xml;

import jakarta.xml.bind.JAXBElement;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import nl.qunit.bpmnmeister.bpmn.TEventDefinition;
import nl.qunit.bpmnmeister.bpmn.TTimerEventDefinition;
import nl.qunit.bpmnmeister.pd.model.EventDefinition;
import nl.qunit.bpmnmeister.pd.model.TimerEventDefinition;

public class EventDefinitionMapper {
  private EventDefinitionMapper() {}

  public static Set<EventDefinition> map(
      List<JAXBElement<? extends TEventDefinition>> eventDefinition, String parentId) {
    return eventDefinition.stream()
        .map(JAXBElement::getValue)
        .map(ed -> mapEventDefinition(ed, parentId))
        .collect(Collectors.toSet());
  }

  private static EventDefinition mapEventDefinition(TEventDefinition ed, String parentId) {
    if (ed instanceof TTimerEventDefinition timerEventDefinition) {
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
          new String(timerEventDefinition.getId()), parentId, duration, cycle, timeDate);
    }
    throw new IllegalStateException("Unknown event definition: " + ed.getClass().getName());
  }
}
