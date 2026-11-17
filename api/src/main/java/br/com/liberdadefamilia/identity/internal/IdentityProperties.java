package br.com.liberdadefamilia.identity.internal;

import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Where the web app lives and how passkeys name this site. */
@Validated
@ConfigurationProperties("liberdade")
record IdentityProperties(
        @NotNull @Valid Web web, @NotNull @Valid WebAuthn webauthn) {

    private static final String ORIGIN_HOST = "^https?://([^:/]+)(:\\d+)?$";

    /**
     * A passkey is bound to a domain the browser accepts for the page: the host of the web origin or a parent of it.
     * Anything else fails at startup instead of at the first sign-in. A missing or malformed value is reported by the
     * field constraints, so this stays quiet then.
     */
    @AssertTrue(message = "liberdade.webauthn.rp-id must be the host of liberdade.web.origin or a parent domain of it")
    boolean isRpIdWithinOrigin() {
        if (web == null || web.origin() == null || webauthn == null || webauthn.rpId() == null) {
            return true;
        }
        String host = web.origin().replaceFirst(ORIGIN_HOST, "$1");
        if (host.equals(web.origin())) {
            return true;
        }
        return host.equals(webauthn.rpId()) || host.endsWith("." + webauthn.rpId());
    }

    /**
     * @param origin scheme, host and optional port, such as {@code https://app.example.com}: {@code https} with a
     *     lowercase host, or {@code http} only for localhost and 127.0.0.1. A user, path, query, fragment or trailing
     *     slash fails at startup rather than being fixed, since redirects are built as {@code origin + "/entrar"}.
     */
    record Web(
            @NotBlank
            @Pattern(regexp = "https://[a-z0-9.-]+(:\\d{1,5})?|http://(localhost|127\\.0\\.0\\.1)(:\\d{1,5})?")
            String origin) {}

    /**
     * @param rpId the domain passkeys are bound to, such as {@code app.example.com}
     * @param rpName the site name the device shows next to the passkey
     */
    record WebAuthn(@NotBlank String rpId, @NotBlank String rpName) {}
}
