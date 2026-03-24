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

  /**
   * Actor role of this key — determines what operations the key is trusted to authorize.
   *
   * <p>Field is declared last to preserve CBOR array order for backward compatibility. Pre-role
   * keys (without this field) deserialize to {@code null}; use {@link #effectiveRole()} to get the
   * safe default.
   */
  @Builder.Default private KeyRole role = KeyRole.CLIENT;

  /**
   * Optional RSA/SHA-256 countersignature produced by the platform's root private key over the
   * canonical payload of this key entry (see {@link
   * io.taktx.security.SigningKeyRegistrar#computeCanonicalPayload(SigningKeyDTO)}).
   *
   * <p>In <em>anchored mode</em> ({@code TAKTX_PLATFORM_PUBLIC_KEY} is configured on the engine),
   * {@code AnchoredKeyTrustPolicy} requires this field to be present and cryptographically valid on
   * <strong>all</strong> key roles — both {@link KeyRole#ENGINE} and {@link KeyRole#CLIENT}. Keys
   * without a valid countersignature are rejected.
   *
   * <p>In <em>community mode</em> ({@code TAKTX_PLATFORM_PUBLIC_KEY} is absent) this field is
   * ignored and may be {@code null}; {@code OpenKeyTrustPolicy} applies instead.
   *
   * <p>The value is the base64-encoded result of:
   *
   * <pre>{@code
   * printf '%s|%s|%s|%s|%s' "$KEY_ID" "$PUBLIC_KEY_B64" "$ALGORITHM" "$OWNER" "$ROLE" \
   *   | openssl dgst -sha256 -sign platform-private.pem \
   *   | base64
   * }</pre>
   *
   * See {@code scripts/generate_trust_anchor.sh} for the complete operator workflow.
   */
  private String registrationSignature;

  /**
   * Returns the effective role, treating {@code null} (pre-role keys) as {@link KeyRole#CLIENT}.
   */
  public KeyRole effectiveRole() {
    return role != null ? role : KeyRole.CLIENT;
  }
}
