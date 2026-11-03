package br.com.liberdadefamilia.support;

import com.jayway.jsonpath.JsonPath;
import java.io.ByteArrayOutputStream;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.security.Signature;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import tools.jackson.databind.json.JsonMapper;

/**
 * A passkey in software: one ES256 key with {@code "none"} attestation, just enough of WebAuthn to register and sign in
 * through the real endpoints, the way a browser would.
 */
public final class SoftwareAuthenticator {

    private static final int USER_PRESENT = 0x01;
    private static final int USER_VERIFIED = 0x04;
    private static final int ATTESTED_CREDENTIAL_DATA = 0x40;
    private static final Base64.Encoder BASE64URL = Base64.getUrlEncoder().withoutPadding();
    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final String origin;
    private final byte[] rpIdHash;
    private final KeyPair keys;
    private final byte[] credentialId = new byte[16];
    private int signCount;
    private String userHandle = "";

    public SoftwareAuthenticator(String rpId, String origin) {
        this.origin = origin;
        try {
            this.rpIdHash = sha256(rpId.getBytes(StandardCharsets.UTF_8));
            KeyPairGenerator generator = KeyPairGenerator.getInstance("EC");
            generator.initialize(new ECGenParameterSpec("secp256r1"));
            this.keys = generator.generateKeyPair();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
        new SecureRandom().nextBytes(credentialId);
    }

    /** The body for {@code POST /webauthn/register}, answering the options from {@code /webauthn/register/options}. */
    public String register(String creationOptionsJson, String label) {
        userHandle = JsonPath.read(creationOptionsJson, "$.user.id");
        byte[] clientData = clientData("webauthn.create", JsonPath.read(creationOptionsJson, "$.challenge"));

        var authData = new ByteArrayOutputStream();
        authData.writeBytes(rpIdHash);
        authData.write(USER_PRESENT | USER_VERIFIED | ATTESTED_CREDENTIAL_DATA);
        authData.writeBytes(ByteBuffer.allocate(4).putInt(signCount).array());
        authData.writeBytes(new byte[16]); // AAGUID: none
        authData.writeBytes(
                ByteBuffer.allocate(2).putShort((short) credentialId.length).array());
        authData.writeBytes(credentialId);
        authData.writeBytes(coseKey());
        byte[] attestationObject = new Cbor()
                .map(3)
                .text("fmt")
                .text("none")
                .text("attStmt")
                .map(0)
                .text("authData")
                .bytes(authData.toByteArray())
                .toByteArray();

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("attestationObject", BASE64URL.encodeToString(attestationObject));
        response.put("clientDataJSON", BASE64URL.encodeToString(clientData));
        response.put("transports", List.of("internal"));
        return JSON.writeValueAsString(Map.of("publicKey", Map.of("credential", credential(response), "label", label)));
    }

    /** The body for {@code POST /login/webauthn}, answering the options from {@code /webauthn/authenticate/options}. */
    public String authenticate(String requestOptionsJson) {
        byte[] clientData = clientData("webauthn.get", JsonPath.read(requestOptionsJson, "$.challenge"));
        byte[] authData = ByteBuffer.allocate(32 + 1 + 4)
                .put(rpIdHash)
                .put((byte) (USER_PRESENT | USER_VERIFIED))
                .putInt(++signCount)
                .array();
        byte[] signature;
        try {
            Signature signer = Signature.getInstance("SHA256withECDSA");
            signer.initSign(keys.getPrivate());
            signer.update(authData);
            signer.update(sha256(clientData));
            signature = signer.sign();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("authenticatorData", BASE64URL.encodeToString(authData));
        response.put("clientDataJSON", BASE64URL.encodeToString(clientData));
        response.put("signature", BASE64URL.encodeToString(signature));
        response.put("userHandle", userHandle);
        return JSON.writeValueAsString(credential(response));
    }

    private Map<String, Object> credential(Map<String, Object> response) {
        Map<String, Object> credential = new LinkedHashMap<>();
        credential.put("id", BASE64URL.encodeToString(credentialId));
        credential.put("rawId", BASE64URL.encodeToString(credentialId));
        credential.put("response", response);
        credential.put("type", "public-key");
        credential.put("clientExtensionResults", Map.of());
        credential.put("authenticatorAttachment", "platform");
        return credential;
    }

    private byte[] clientData(String type, String challenge) {
        Map<String, Object> clientData = new LinkedHashMap<>();
        clientData.put("type", type);
        clientData.put("challenge", challenge);
        clientData.put("origin", origin);
        clientData.put("crossOrigin", false);
        return JSON.writeValueAsBytes(clientData);
    }

    /** The public key as a COSE_Key: EC2 (kty 2), ES256 (alg -7), P-256 (crv 1), x and y. */
    private byte[] coseKey() {
        ECPublicKey publicKey = (ECPublicKey) keys.getPublic();
        return new Cbor()
                .map(5)
                .integer(1)
                .integer(2)
                .integer(3)
                .integer(-7)
                .integer(-1)
                .integer(1)
                .integer(-2)
                .bytes(coordinate(publicKey.getW().getAffineX()))
                .integer(-3)
                .bytes(coordinate(publicKey.getW().getAffineY()))
                .toByteArray();
    }

    /** A P-256 coordinate as exactly 32 unsigned big-endian bytes. */
    private static byte[] coordinate(BigInteger value) {
        byte[] raw = value.toByteArray();
        byte[] fixed = new byte[32];
        int length = Math.min(raw.length, 32);
        System.arraycopy(raw, raw.length - length, fixed, 32 - length, length);
        return fixed;
    }

    private static byte[] sha256(byte[] input) throws GeneralSecurityException {
        return MessageDigest.getInstance("SHA-256").digest(input);
    }

    /** The few CBOR items needed here: small integers, byte and text strings, and maps. */
    private static final class Cbor {

        private final ByteArrayOutputStream out = new ByteArrayOutputStream();

        Cbor map(int entries) {
            return head(5, entries);
        }

        Cbor integer(long value) {
            return value >= 0 ? head(0, value) : head(1, -1 - value);
        }

        Cbor bytes(byte[] value) {
            head(2, value.length);
            out.writeBytes(value);
            return this;
        }

        Cbor text(String value) {
            byte[] utf8 = value.getBytes(StandardCharsets.UTF_8);
            head(3, utf8.length);
            out.writeBytes(utf8);
            return this;
        }

        /** The initial byte, then the argument in 0, 1 or 2 more bytes. */
        private Cbor head(int majorType, long argument) {
            int initial = majorType << 5;
            if (argument < 24) {
                out.write(initial | (int) argument);
            } else if (argument < 0x100) {
                out.write(initial | 24);
                out.write((int) argument);
            } else {
                out.write(initial | 25);
                out.write((int) (argument >> 8) & 0xff);
                out.write((int) argument & 0xff);
            }
            return this;
        }

        byte[] toByteArray() {
            return out.toByteArray();
        }
    }
}
