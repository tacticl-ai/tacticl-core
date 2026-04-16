package io.tacticl.data.connections.entity;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class SecretMetadataTest {

    @Test
    void create_setsFieldsCorrectly() {
        SecretMetadata secret = SecretMetadata.create("user-1", "MY_KEY", "OpenAI");

        assertThat(secret.getUserId()).isEqualTo("user-1");
        assertThat(secret.getName()).isEqualTo("MY_KEY");
        assertThat(secret.getProviderHint()).isEqualTo("OpenAI");
        assertThat(secret.getId()).isNotBlank();
        assertThat(secret.getVaultPath()).isEqualTo("tacticl/user-1/secrets/" + secret.getId());
        assertThat(secret.getLastTestResult()).isEqualTo(TestResult.UNTESTED);
        assertThat(secret.getCreatedAt()).isNotNull();
        assertThat(secret.getLastTestedAt()).isNull();
    }

    @Test
    void markTested_updatesResultAndTimestamp() {
        SecretMetadata secret = SecretMetadata.create("user-1", "MY_KEY", "OpenAI");

        secret.markTested(TestResult.VALID);

        assertThat(secret.getLastTestResult()).isEqualTo(TestResult.VALID);
        assertThat(secret.getLastTestedAt()).isNotNull();
    }
}
