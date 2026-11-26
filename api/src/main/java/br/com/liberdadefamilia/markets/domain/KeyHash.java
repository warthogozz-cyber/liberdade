package br.com.liberdadefamilia.markets.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/** What the family stores instead of an access key: SHA-256 of {@code workspaceId|key}, lowercase hex. */
public final class KeyHash {

    private KeyHash() {}

    public static String of(UUID workspaceId, NfceKey key) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest((workspaceId + "|" + key.digits()).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
