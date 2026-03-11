package io.taktx.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.quarkus.runtime.annotations.RegisterForReflection;
import java.time.Instant;
import lombok.*;

/**
 * Public signing key distributed to all participants via the compacted {@code taktx-signing-keys}
 * topic. The Kafka record key is the {@code keyId}.
 *
 * <h3>Key lifecycle for zero-downtime rotation</h3>
 *
 * <ul>
 *   <li>{@code ACTIVE} — currently used for signing and accepted for verification
 *   <li>{@code TRUSTED} — no longer used for signing but still accepted during the drain window
 *       (consumers catching up before old signatures are rejected)
 *   <li>{@code REVOKED} — must be rejected immediately for both signing and verification
 * </ul>
 */
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@EqualsAndHashCode
@ToString
@RegisterForReflection
@JsonFormat(shape = JsonFormat.Shape.ARRAY)
public class SigningKeyDTO {

  /** Lifecycle status of a distributed public key. */
  public enum KeyStatus {
    /** Key is actively used for signing and accepted for verification. */
    ACTIVE,
    /**
     * Key is no longer used for signing but still accepted during the overlap/drain window.
     * Consumers that have not yet seen the new ACTIVE key will still accept messages signed with
     * this key.
     */
    TRUSTED,
    /** Key must be rejected immediately — treat any signature from this key as invalid. */
    REVOKED
  }

  private String keyId;
  private String publicKeyBase64;

  /** Signing algorithm — {@code "Ed25519"} or {@code "RSA"}. */
  private String algorithm;

  private Instant createdAt;

  /** Rotation lifecycle status — replaces the old {@code boolean active} field. */
  @Builder.Default private KeyStatus status = KeyStatus.ACTIVE;

  /**
   * Human-readable owner label, e.g. {@code "engine"}, {@code "worker-billing"}, {@code
   * "platform"}.
   */
  private String owner;
}
