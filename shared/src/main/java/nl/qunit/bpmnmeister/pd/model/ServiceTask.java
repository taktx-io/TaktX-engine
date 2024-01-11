package nl.qunit.bpmnmeister.pd.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Set;
import lombok.Getter;
import lombok.experimental.SuperBuilder;

@Getter
@SuperBuilder
public class ServiceTask extends Task {

  @JsonCreator
  public ServiceTask(
      @JsonProperty("id") String id,
      @JsonProperty("incoming") Set<String> incoming,
      @JsonProperty("outgoing") Set<String> outgoing) {
    super(id, incoming, outgoing);
  }
}
