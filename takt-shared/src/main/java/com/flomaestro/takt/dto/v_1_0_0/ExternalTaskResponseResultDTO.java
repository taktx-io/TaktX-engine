package com.flomaestro.takt.dto.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@EqualsAndHashCode
public class ExternalTaskResponseResultDTO {
  @JsonProperty("rt")
  private ExternalTaskResponseType responseType;

  @JsonProperty("n")
  private String name;

  @JsonProperty("c")
  private String code;

  @JsonProperty("m")
  private String message;

  @JsonProperty("ar")
  private Boolean allowRetry;

  public ExternalTaskResponseResultDTO(
      ExternalTaskResponseType responseType,
      Boolean allowRetry,
      String name,
      String message,
      String code) {
    this.responseType = responseType;
    this.name = name;
    this.code = code;
    this.message = message;
    this.allowRetry = allowRetry;
  }
}
