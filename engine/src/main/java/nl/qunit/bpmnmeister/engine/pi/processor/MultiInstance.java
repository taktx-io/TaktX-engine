package nl.qunit.bpmnmeister.engine.pi.processor;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import nl.qunit.bpmnmeister.pd.model.Activity;
import nl.qunit.bpmnmeister.pd.model.BaseElement;
import nl.qunit.bpmnmeister.pd.model.BaseElementId;

@Getter
public class MultiInstance extends BaseElement {

  private final Activity activity;

  protected MultiInstance(
      @JsonProperty("id") BaseElementId id,
      @JsonProperty("parentId") BaseElementId parentId,
      @JsonProperty("activity") Activity activity) {
    super(id, parentId);
    this.activity = activity;
  }
}
