package com.flomaestro.takt.dto.v_1_0_0;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@NoArgsConstructor
@EqualsAndHashCode
@ToString
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExternalTaskResponseResultDTO {
  @JsonProperty("rt")
  private ExternalTaskResponseType responseType;

  @JsonProperty("to")
  private long timeout;

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
      String code,
      long timeout) {
    this.responseType = responseType;
    this.name = name;
    this.code = code;
    this.message = message;
    this.allowRetry = allowRetry;
    this.timeout = timeout;
  }
}
