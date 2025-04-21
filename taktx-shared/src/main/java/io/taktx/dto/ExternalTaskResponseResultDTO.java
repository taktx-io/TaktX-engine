package io.taktx.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonFormat.Shape;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@NoArgsConstructor
@EqualsAndHashCode
@ToString
@JsonFormat(shape = Shape.ARRAY)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ExternalTaskResponseResultDTO {
  private ExternalTaskResponseType responseType;

  private long timeout;

  private String name;

  private String code;

  private String message;

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
