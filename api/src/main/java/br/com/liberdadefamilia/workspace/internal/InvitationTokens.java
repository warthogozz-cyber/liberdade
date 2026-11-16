package br.com.liberdadefamilia.workspace.internal;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;

/** The secret in an invitation link. Only its SHA-256 is stored, so a leaked table opens no link. */
final class InvitationTokens {

    /** The length of a token: 32 bytes as unpadded base64url. */
    static final int TOKEN_LENGTH = 43;

    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int TOKEN_BYTES = 32;

    private InvitationTokens() {}

    /** 32 random bytes as unpadded base64url: 43 characters. */
    static String newToken() {
        byte[] bytes = new byte[TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** The SHA-256 of the token's UTF-8 bytes: what the database keeps and looks up. */
    static byte[] hash(String token) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is part of every Java runtime", e);
        }
    }
}
