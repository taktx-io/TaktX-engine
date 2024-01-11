package nl.qunit.bpmnmeister.pd.xml;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.xml.bind.JAXBElement;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import nl.qunit.bpmnmeister.bpmn.TEventDefinition;
import nl.qunit.bpmnmeister.bpmn.TTimerEventDefinition;
import nl.qunit.bpmnmeister.pd.model.EventDefinition;
import nl.qunit.bpmnmeister.pd.model.TimerEventDefinition;

@ApplicationScoped
public class EventDefinitionMapper {
  public Set<EventDefinition> map(List<JAXBElement<? extends TEventDefinition>> eventDefinition) {
    return eventDefinition.stream()
        .map(JAXBElement::getValue)
        .map(this::mapEventDefinition)
        .collect(Collectors.toSet());
  }

  private EventDefinition mapEventDefinition(TEventDefinition ed) {
    if (ed instanceof TTimerEventDefinition timerEventDefinition) {
      String duration =
          timerEventDefinition.getTimeDuration() != null
              ? timerEventDefinition.getTimeDuration().getContent().stream()
                  .map(Object::toString)
                  .collect(Collectors.joining(""))
              : null;
      String cycle =
          timerEventDefinition.getTimeCycle() != null
              ? timerEventDefinition.getTimeCycle().getContent().stream()
                  .map(Object::toString)
                  .collect(Collectors.joining(""))
              : null;
      String timeDate =
          timerEventDefinition.getTimeDate() != null
              ? timerEventDefinition.getTimeDate().getContent().stream()
                  .map(Object::toString)
                  .collect(Collectors.joining(""))
              : null;
      return TimerEventDefinition.builder()
          .id(timerEventDefinition.getId())
          .timeDuration(duration)
          .timeCycle(cycle)
          .timeDate(timeDate)
          .build();
    }
    return null;
  }
}
