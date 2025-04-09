package io.taktx.xml;

import io.taktx.bpmn.Subscription;
import io.taktx.bpmn.TMessage;
import io.taktx.dto.MessageDTO;

public class ZeebeMessagekMapper implements MessageMapper {

  private static boolean test(Object e) {
    return e instanceof Subscription;
  }

  @Override
  public MessageDTO map(TMessage tMessage) {
    String correlationKey = null;
    if (tMessage.getExtensionElements() != null) {
      correlationKey =
          tMessage.getExtensionElements().getAny().stream()
              .filter(ZeebeMessagekMapper::test)
              .map(e -> ((Subscription) e).getCorrelationKey())
              .findFirst()
              .orElse(null);
    }
    return new MessageDTO(tMessage.getId(), tMessage.getName(), correlationKey);
  }
}
