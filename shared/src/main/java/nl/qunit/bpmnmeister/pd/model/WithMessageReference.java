package nl.qunit.bpmnmeister.pd.model;

public interface WithMessageReference {
  String getMessageRef();

  Message getReferencedMessage();

  void setReferencedMessage(Message message);
}
