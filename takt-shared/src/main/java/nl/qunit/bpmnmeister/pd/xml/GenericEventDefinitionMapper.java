package nl.qunit.bpmnmeister.pd.xml;

import jakarta.xml.bind.JAXBElement;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import nl.qunit.bpmnmeister.bpmn.TErrorEventDefinition;
import nl.qunit.bpmnmeister.bpmn.TEscalationEventDefinition;
import nl.qunit.bpmnmeister.bpmn.TEventDefinition;
import nl.qunit.bpmnmeister.bpmn.TLinkEventDefinition;
import nl.qunit.bpmnmeister.bpmn.TMessageEventDefinition;
import nl.qunit.bpmnmeister.bpmn.TTerminateEventDefinition;
import nl.qunit.bpmnmeister.bpmn.TTimerEventDefinition;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.ErrorEventDefinitionDTO;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.EscalationEventDefinitionDTO;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.EventDefinitionDTO;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.LinkEventDefinitionDTO;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.MessageEventDefinitionDTO;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.TerminateEventDefinitionDTO;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.TimerEventDefinitionDTO;

public class GenericEventDefinitionMapper implements EventDefinitionMapper {

  public Set<EventDefinitionDTO> map(
      List<JAXBElement<? extends TEventDefinition>> eventDefinition, String parentId) {
    return eventDefinition.stream()
        .map(JAXBElement::getValue)
        .map(ed -> mapEventDefinition(ed, parentId))
        .collect(Collectors.toSet());
  }

  private EventDefinitionDTO mapEventDefinition(TEventDefinition ed, String parentId) {
    if (ed instanceof TTimerEventDefinition timerEventDefinition) {
      return mapTimerEventDefinition(parentId, timerEventDefinition);
    } else if (ed instanceof TMessageEventDefinition messageEventDefinition) {
      return mapMessageEventDefinition(messageEventDefinition);
    } else if (ed instanceof TLinkEventDefinition linkEventDefinition) {
      return mapLinkEventDefinition(linkEventDefinition);
    } else if (ed instanceof TTerminateEventDefinition terminateEventDefinition) {
      return mapTerminateEventDefinition(terminateEventDefinition);
    } else if (ed instanceof TEscalationEventDefinition escalationEventDefinition) {
      return mapEscalationEventDefinition(escalationEventDefinition);
    } else if (ed instanceof TErrorEventDefinition errorEventDefinition) {
      return mapErrorEventDefinition(errorEventDefinition);
    }
    throw new IllegalStateException("Unknown event definition: " + ed.getClass().getName());
  }

  private EventDefinitionDTO mapErrorEventDefinition(TErrorEventDefinition errorEventDefinition) {
    return new ErrorEventDefinitionDTO(
        errorEventDefinition.getId(),
        errorEventDefinition.getErrorRef() != null
            ? errorEventDefinition.getErrorRef().getLocalPart()
            : null);
  }

  private EventDefinitionDTO mapEscalationEventDefinition(
      TEscalationEventDefinition escalationEventDefinition) {
    return new EscalationEventDefinitionDTO(
        escalationEventDefinition.getId(),
        escalationEventDefinition.getEscalationRef() != null
            ? escalationEventDefinition.getEscalationRef().getLocalPart()
            : null);
  }

  private EventDefinitionDTO mapTerminateEventDefinition(
      TTerminateEventDefinition terminateEventDefinition) {
    return new TerminateEventDefinitionDTO(terminateEventDefinition.getId());
  }

  private EventDefinitionDTO mapLinkEventDefinition(TLinkEventDefinition linkEventDefinition) {
    return new LinkEventDefinitionDTO(linkEventDefinition.getId(), linkEventDefinition.getName());
  }

  private static MessageEventDefinitionDTO mapMessageEventDefinition(
      TMessageEventDefinition messageEventDefinition) {
    return new MessageEventDefinitionDTO(
        messageEventDefinition.getId(), messageEventDefinition.getMessageRef().getLocalPart());
  }

  private TimerEventDefinitionDTO mapTimerEventDefinition(
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
    return new TimerEventDefinitionDTO(
        timerEventDefinition.getId(), parentId, timeDate, duration, cycle);
  }
}
