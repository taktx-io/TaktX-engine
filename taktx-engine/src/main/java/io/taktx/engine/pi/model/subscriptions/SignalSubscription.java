package io.taktx.engine.pi.model.subscriptions;

import com.fasterxml.jackson.databind.JsonNode;
import io.taktx.dto.subscriptions.SubScriptionType;
import io.taktx.engine.feel.FeelExpressionHandler;
import io.taktx.engine.pd.model.EventSignal;
import io.taktx.engine.pd.model.FlowNode;
import io.taktx.engine.pd.model.SignalEventDefinition;
import io.taktx.engine.pi.ProcessInstanceException;
import io.taktx.engine.pi.ProcessInstanceProcessingContext;
import io.taktx.engine.pi.model.CancelInstanceSignalSubscriptionInfo;
import io.taktx.engine.pi.model.FlowNodeInstance;
import io.taktx.engine.pi.model.IFlowNodeInstance;
import io.taktx.engine.pi.model.NewInstanceSignalSubscriptionInfo;
import io.taktx.engine.pi.model.Scope;
import io.taktx.engine.pi.model.SignalEventSignal;
import io.taktx.engine.pi.model.VariableScope;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class SignalSubscription extends Subscription {
  private String name;

  public SignalSubscription() {
    setOrder(5);
  }

  private SignalSubscription(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      IFlowNodeInstance flowNodeInstance,
      String flowNodeId,
      SubScriptionType subScriptionType,
      SignalEventDefinition signalEventDefinition,
      VariableScope variableScope,
      FeelExpressionHandler feelExpressionHandler) {
    super(0, subScriptionType, flowNodeId);
    if (signalEventDefinition.getReferencedSignal() == null) {
      throw new ProcessInstanceException(null, "SignalEventDefinition has no referenced signal");
    }

    JsonNode jsonNode =
        feelExpressionHandler.processFeelExpression(
            signalEventDefinition.getReferencedSignal().name(), variableScope);

    if (jsonNode == null || jsonNode.isNull()) {
      throw new ProcessInstanceException(null, "Signal name expression returned null");
    }

    name = jsonNode.asText();
    NewInstanceSignalSubscriptionInfo subscriptionInfo =
        new NewInstanceSignalSubscriptionInfo(name, flowNodeInstance);

    processInstanceProcessingContext
        .getInstanceResult()
        .addNewInstanceSignalSubscription(subscriptionInfo);
  }

  public static SignalSubscription starting(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      IFlowNodeInstance flowNodeInstance,
      FlowNode flowNode,
      SignalEventDefinition timerEventDefinition,
      VariableScope variableScope,
      FeelExpressionHandler feelExpressionHandler) {
    return new SignalSubscription(
        processInstanceProcessingContext,
        flowNodeInstance,
        flowNode.getId(),
        SubScriptionType.STARTING,
        timerEventDefinition,
        variableScope,
        feelExpressionHandler);
  }

  public static SignalSubscription continuing(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      IFlowNodeInstance flowNodeInstance,
      SignalEventDefinition signalEventDefinition,
      VariableScope variableScope,
      FeelExpressionHandler feelExpressionHandler) {
    return new SignalSubscription(
        processInstanceProcessingContext,
        flowNodeInstance,
        null,
        SubScriptionType.CONTINUING,
        signalEventDefinition,
        variableScope,
        feelExpressionHandler);
  }

  @Override
  public boolean matchesEvent(EventSignal event) {
    return event instanceof SignalEventSignal signalEventSignal
        && signalEventSignal.getName().equals(this.name);
  }

  @Override
  public void cancel(
      ProcessInstanceProcessingContext processInstanceProcessingContext,
      Scope scope,
      FlowNodeInstance<?> instance) {
    CancelInstanceSignalSubscriptionInfo cancelInfo =
        new CancelInstanceSignalSubscriptionInfo(name, instance);
    processInstanceProcessingContext
        .getInstanceResult()
        .addCancelInstanceSignalSubscription(cancelInfo);
  }
}
