package io.taktx.engine.generic;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonFormat.Shape;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.annotation.JsonDeserialize;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import io.quarkus.runtime.annotations.RegisterForReflection;
import io.taktx.util.TaktLongListDeserializer;
import io.taktx.util.TaktLongListSerializer;
import io.taktx.util.TaktUUIDDeserializer;
import io.taktx.util.TaktUUIDSerializer;
import java.util.List;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Getter
@Setter
@JsonFormat(shape = Shape.ARRAY)
@JsonInclude(Include.NON_NULL)
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@ToString
@RegisterForReflection
public class SignalInstanceSubscriptionKeyDTO {
  private byte[] signalNameHash;

  @JsonSerialize(using = TaktUUIDSerializer.class)
  @JsonDeserialize(using = TaktUUIDDeserializer.class)
  private UUID processInstanceId;

  @JsonSerialize(using = TaktLongListSerializer.class)
  @JsonDeserialize(using = TaktLongListDeserializer.class)
  private List<Long> elementInstanceIdPath;
}
