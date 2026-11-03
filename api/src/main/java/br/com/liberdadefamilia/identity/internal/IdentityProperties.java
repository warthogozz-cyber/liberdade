package br.com.liberdadefamilia.identity.internal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Where the web app lives and how passkeys name this site. */
@ConfigurationProperties("liberdade")
record IdentityProperties(Web web, WebAuthn webauthn) {

    record Web(String origin) {}

    record WebAuthn(String rpId, String rpName) {}
}
