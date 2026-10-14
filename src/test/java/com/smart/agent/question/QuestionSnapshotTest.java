package com.smart.agent.question;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class QuestionSnapshotTest {
    @Test void removesCredentialsAndBoundsContent() {
        String safe = QuestionSnapshot.safe("password=secret Authorization: Bearer abc.def token=xyz " + "x".repeat(9000));
        assertThat(safe).doesNotContain("secret", "abc.def", "xyz").hasSizeLessThanOrEqualTo(2000);
    }
    @Test void onlyFailedTerminalStatesAreAutomatic() {
        assertThat(QuestionSnapshot.automatic("FAILED")).isTrue();
        assertThat(QuestionSnapshot.automatic("TIMEOUT")).isTrue();
        for (String status : new String[]{"COMPLETED", "CANCELLED", "PERMISSION_DENIED", "GENERATING"})
            assertThat(QuestionSnapshot.automatic(status)).isFalse();
    }
    @Test void removesQuotedSecretsIncludingWhitespaceAndStandaloneKeys() {
        assertThat(QuestionSnapshot.safe("{\"password\":\"private secret value\"} sk-abcdefgh1234567890"))
                .doesNotContain("private", "secret value", "sk-abcdefgh1234567890");
    }
    @Test void removesSingleQuotedSecretsIncludingSpacesAndNewlines() {
        assertThat(QuestionSnapshot.safe("password='private secret value' token='first\nsecond\r\nthird'"))
                .doesNotContain("private", "secret value", "first", "second", "third");
    }
    @Test void removesMultilineDoubleQuotedSecrets() {
        assertThat(QuestionSnapshot.safe("{\"secret\":\"first\nsecond\r\nthird\"}"))
                .doesNotContain("first", "second", "third");
    }
}
