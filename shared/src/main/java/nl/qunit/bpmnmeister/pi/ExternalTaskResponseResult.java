package nl.qunit.bpmnmeister.pi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;

@Getter
public class ExternalTaskResponseResult {

  public static final ExternalTaskResponseResult NONE = new ExternalTaskResponseResult(false, "");
  private final Boolean success;
  private final String errorMessage;

  @JsonCreator
  public ExternalTaskResponseResult(
      @JsonProperty("success") Boolean success, @JsonProperty("errorMessage") String errorMessage) {
    this.success = success;
    this.errorMessage = errorMessage;
  }
}
