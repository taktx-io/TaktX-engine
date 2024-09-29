package nl.qunit.bpmnmeister.pd.model;

public interface WithMessageReference {
  String getMessageRef();

  Message2 getReferencedMessage();

  void setReferencedMessage(Message2 message);
}
