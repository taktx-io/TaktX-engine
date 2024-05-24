package nl.qunit.bpmnmeister.pd.xml;

import nl.qunit.bpmnmeister.bpmn.Subscription;
import nl.qunit.bpmnmeister.bpmn.TMessage;
import nl.qunit.bpmnmeister.pd.model.Constants;
import nl.qunit.bpmnmeister.pd.model.Message;

public class ZeebeMessagekMapper implements MessageMapper {

  private static boolean test(Object e) {
    return e instanceof Subscription;
  }

  @Override
  public Message map(TMessage tMessage) {
    String correlationKey = Constants.NONE;
    if (tMessage.getExtensionElements() != null) {
      correlationKey =
          tMessage.getExtensionElements().getAny().stream()
              .filter(ZeebeMessagekMapper::test)
              .map(e -> ((Subscription) e).getCorrelationKey())
              .findFirst()
              .orElse(Constants.NONE);
    }
    return new Message(tMessage.getId(), tMessage.getName(), correlationKey);
  }
}
