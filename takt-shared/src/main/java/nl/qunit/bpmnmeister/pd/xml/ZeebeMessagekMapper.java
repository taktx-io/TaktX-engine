package nl.qunit.bpmnmeister.pd.xml;

import nl.qunit.bpmnmeister.bpmn.Subscription;
import nl.qunit.bpmnmeister.bpmn.TMessage;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.Constants;
import nl.qunit.bpmnmeister.pd.model.v_1_0_0.MessageDTO;

public class ZeebeMessagekMapper implements MessageMapper {

  private static boolean test(Object e) {
    return e instanceof Subscription;
  }

  @Override
  public MessageDTO map(TMessage tMessage) {
    String correlationKey = Constants.NONE;
    if (tMessage.getExtensionElements() != null) {
      correlationKey =
          tMessage.getExtensionElements().getAny().stream()
              .filter(ZeebeMessagekMapper::test)
              .map(e -> ((Subscription) e).getCorrelationKey())
              .findFirst()
              .orElse(Constants.NONE);
    }
    return new MessageDTO(tMessage.getId(), tMessage.getName(), correlationKey);
  }
}
