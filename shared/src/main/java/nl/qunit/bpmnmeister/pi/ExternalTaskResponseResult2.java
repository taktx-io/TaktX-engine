package nl.qunit.bpmnmeister.pi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import lombok.Getter;
import nl.qunit.bpmnmeister.pd.model.Constants;

@Getter
public class ExternalTaskResponseResult2 {

  public static final ExternalTaskResponseResult2 NONE =
      new ExternalTaskResponseResult2(false, false, Constants.NONE);
  private final Boolean success;
  private final String errorMessage;
  private final Boolean allowRetry;

  @JsonCreator
  public ExternalTaskResponseResult2(
      @Nonnull @JsonProperty("success") Boolean success,
      @JsonProperty("allowRetry") Boolean allowRetry,
      @JsonProperty("errorMessage") String errorMessage) {
    this.success = success;
    this.errorMessage = errorMessage;
    this.allowRetry = allowRetry;
  }
}
