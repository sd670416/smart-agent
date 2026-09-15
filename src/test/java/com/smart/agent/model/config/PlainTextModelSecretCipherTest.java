package com.smart.agent.model.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PlainTextModelSecretCipherTest {

    private final PlainTextModelSecretCipher cipher = new PlainTextModelSecretCipher();

    @Test
    void storesAndReadsApiKeyWithoutRequiringAMasterKey() {
        assertThat(cipher.encrypt("  sk-development-key  ")).isEqualTo("sk-development-key");
        assertThat(cipher.decrypt("sk-development-key")).isEqualTo("sk-development-key");
    }

    @Test
    void treatsBlankValuesAsMissing() {
        assertThat(cipher.encrypt("  ")).isNull();
        assertThat(cipher.decrypt(null)).isNull();
    }

    @Test
    void identifiesLegacyEncryptedValuesThatCannotBeDecrypted() {
        assertThat(cipher.isLegacyEncryptedValue("v1:encrypted-payload")).isTrue();
        assertThat(cipher.isLegacyEncryptedValue("sk-development-key")).isFalse();
    }
}
