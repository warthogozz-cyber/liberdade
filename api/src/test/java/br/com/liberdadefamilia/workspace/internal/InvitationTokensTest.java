package br.com.liberdadefamilia.workspace.internal;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class InvitationTokensTest {

    @Test
    void tokenIs43CharactersOfTheBase64UrlAlphabet() {
        String token = InvitationTokens.newToken();

        assertThat(token).hasSize(43).matches("[A-Za-z0-9_-]{43}");
    }

    @Test
    void aThousandTokensAreDistinct() {
        Set<String> tokens = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            tokens.add(InvitationTokens.newToken());
        }

        assertThat(tokens).hasSize(1000);
    }

    @Test
    void hashIs32BytesAndDeterministic() {
        String token = InvitationTokens.newToken();

        assertThat(InvitationTokens.hash(token)).hasSize(32).isEqualTo(InvitationTokens.hash(token));
        assertThat(InvitationTokens.hash(token)).isNotEqualTo(InvitationTokens.hash(InvitationTokens.newToken()));
    }
}
