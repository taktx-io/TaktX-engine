package nl.qunit.bpmnmeister.pi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import lombok.Getter;
import nl.qunit.bpmnmeister.pd.model.Constants;

@Getter
public class ExternalTaskResponseResult {

  public static final ExternalTaskResponseResult NONE = new ExternalTaskResponseResult(false, false, Constants.NONE);
  private final Boolean success;
  private final String errorMessage;
  private final Boolean allowRetry;

  @JsonCreator
  public ExternalTaskResponseResult(
      @Nonnull @JsonProperty("success") Boolean success,
      @JsonProperty("allowRetry") Boolean allowRetry,
      @JsonProperty("errorMessage") String errorMessage) {
    this.success = success;
    this.errorMessage = errorMessage;
    this.allowRetry = allowRetry;
  }
}
