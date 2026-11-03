package br.com.liberdadefamilia.identity.internal;

import jakarta.validation.Valid;
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

    /**
     * @param origin scheme, host and optional port, such as {@code https://app.example.com}. A path or a trailing
     *     slash fails at startup rather than being fixed, since redirects are built as {@code origin + "/entrar"}.
     */
    record Web(
            @NotBlank @Pattern(regexp = "https?://[^/?#\\s]+")
            String origin) {}

    /**
     * @param rpId the domain passkeys are bound to, such as {@code app.example.com}
     * @param rpName the site name the device shows next to the passkey
     */
    record WebAuthn(@NotBlank String rpId, @NotBlank String rpName) {}
}
