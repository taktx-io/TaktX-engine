package io.taktx.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.quarkus.runtime.annotations.RegisterForReflection;
import java.time.Instant;
import lombok.*;

/** Public signing key distributed to consumers via taktx-signing-keys topic. */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode
@ToString
@RegisterForReflection
@JsonFormat(shape = JsonFormat.Shape.ARRAY)
public class SigningKeyDTO {
  private String keyId;
  private String publicKeyBase64;
  private String algorithm;
  private Instant createdAt;
  private boolean active;
}
