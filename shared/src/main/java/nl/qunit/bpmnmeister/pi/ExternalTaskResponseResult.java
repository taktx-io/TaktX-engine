package nl.qunit.bpmnmeister.pi;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nonnull;
import lombok.Getter;

@Getter
public class ExternalTaskResponseResult {
  private final ExternalTaskResponseTypeEnum responseType;
  private final String name;
  private final String code;
  private final String message;
  private final Boolean allowRetry;

  @JsonCreator
  public ExternalTaskResponseResult(
      @Nonnull @JsonProperty("success") ExternalTaskResponseTypeEnum responseType,
      @JsonProperty("allowRetry") Boolean allowRetry,
      @JsonProperty("name") String name,
      @JsonProperty("message") String message,
      @JsonProperty("code") String code) {
    this.responseType = responseType;
    this.name = name;
    this.code = code;
    this.message = message;
    this.allowRetry = allowRetry;
  }
}
