package br.com.liberdadefamilia.support;

import java.security.SecureRandom;
import java.util.UUID;

/** Ids the way a device makes them for a new transaction. */
public final class TestIds {

    private static final SecureRandom RANDOM = new SecureRandom();

    private TestIds() {}

    /** A UUIDv7 (RFC 9562): 48 bits of Unix milliseconds, version 7, the IETF variant and random bits. */
    public static UUID v7() {
        long millis = System.currentTimeMillis();
        long mostSignificant = (millis << 16) | 0x7000L | (RANDOM.nextLong() & 0x0FFFL);
        long leastSignificant = (RANDOM.nextLong() & 0x3FFF_FFFF_FFFF_FFFFL) | 0x8000_0000_0000_0000L;
        return new UUID(mostSignificant, leastSignificant);
    }
}
