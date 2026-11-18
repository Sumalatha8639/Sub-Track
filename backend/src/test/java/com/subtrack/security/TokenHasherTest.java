package com.subtrack.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

class TokenHasherTest {

    private final TokenHasher hasher = new TokenHasher();

    @Test
    void tokensAreUrlSafeAndUnique() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            String token = hasher.newToken();
            assertThat(token).matches("[A-Za-z0-9_-]{43}");
            assertThat(seen.add(token)).isTrue();
        }
    }

    @Test
    void hashIsStableSha256Hex() {
        assertThat(hasher.hash("abc"))
                .isEqualTo("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad");
        assertThat(hasher.hash("abc")).hasSize(64).isEqualTo(hasher.hash("abc"));
    }
}
