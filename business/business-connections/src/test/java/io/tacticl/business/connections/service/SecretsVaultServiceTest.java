package io.tacticl.business.connections.service;

import io.cidadel.framework.secrets.client.VaultClient;
import io.tacticl.data.connections.entity.SecretMetadata;
import io.tacticl.data.connections.entity.TestResult;
import io.tacticl.data.connections.repository.SecretMetadataRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SecretsVaultServiceTest {

    @Mock SecretMetadataRepository repository;
    @Mock VaultClient vaultClient;
    @InjectMocks SecretsVaultService service;

    @Test
    void store_savesMetadataAndWritesToVault() {
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        SecretMetadata result = service.store("user-1", "MY_KEY", "OpenAI", "sk-test-value");
        assertThat(result.getName()).isEqualTo("MY_KEY");
        assertThat(result.getProviderHint()).isEqualTo("OpenAI");
        ArgumentCaptor<Map> captor = ArgumentCaptor.forClass(Map.class);
        verify(vaultClient).write(eq(result.getVaultPath()), captor.capture());
        assertThat(captor.getValue()).containsEntry("value", "sk-test-value");
    }

    @Test
    void listSecrets_returnsUserSecrets() {
        SecretMetadata s = SecretMetadata.create("user-1", "MY_KEY", "OpenAI");
        when(repository.findByUserId("user-1")).thenReturn(List.of(s));
        List<SecretMetadata> result = service.listSecrets("user-1");
        assertThat(result).hasSize(1);
    }

    @Test
    void delete_removesFromVaultAndMongo() {
        SecretMetadata s = SecretMetadata.create("user-1", "MY_KEY", "OpenAI");
        when(repository.findByIdAndUserId("secret-1", "user-1")).thenReturn(Optional.of(s));
        service.delete("user-1", "secret-1");
        verify(vaultClient).delete(s.getVaultPath());
        verify(repository).deleteByIdAndUserId("secret-1", "user-1");
    }

    @Test
    void delete_wrongUser_doesNothing() {
        when(repository.findByIdAndUserId("secret-1", "other-user")).thenReturn(Optional.empty());
        service.delete("other-user", "secret-1");
        verify(vaultClient, never()).delete(any());
        verify(repository, never()).deleteByIdAndUserId(any(), any());
    }

    @Test
    void resolveValue_readsFromVault() {
        SecretMetadata s = SecretMetadata.create("user-1", "MY_KEY", "OpenAI");
        when(repository.findByIdAndUserId(s.getId(), "user-1")).thenReturn(Optional.of(s));
        when(vaultClient.read(s.getVaultPath())).thenReturn(Map.of("value", "sk-real-value"));
        Optional<String> value = service.resolveValue("user-1", s.getId());
        assertThat(value).contains("sk-real-value");
    }

    @Test
    void testSecret_valid_returnsValidAndUpdatesMetadata() {
        SecretMetadata s = SecretMetadata.create("user-1", "MY_KEY", "OpenAI");
        when(repository.findByIdAndUserId(s.getId(), "user-1")).thenReturn(Optional.of(s));
        when(vaultClient.read(s.getVaultPath())).thenReturn(Map.of("value", "sk-test-value"));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        TestResult result = service.testSecret("user-1", s.getId());
        assertThat(result).isEqualTo(TestResult.VALID);
        assertThat(s.getLastTestResult()).isEqualTo(TestResult.VALID);
        assertThat(s.getLastTestedAt()).isNotNull();
    }

    @Test
    void testSecret_vaultReturnsNoValue_returnsInvalid() {
        SecretMetadata s = SecretMetadata.create("user-1", "MY_KEY", "OpenAI");
        when(repository.findByIdAndUserId(s.getId(), "user-1")).thenReturn(Optional.of(s));
        when(vaultClient.read(s.getVaultPath())).thenReturn(Map.of());
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        TestResult result = service.testSecret("user-1", s.getId());
        assertThat(result).isEqualTo(TestResult.INVALID);
    }

    @Test
    void testSecret_vaultThrows_returnsUnreachable() {
        SecretMetadata s = SecretMetadata.create("user-1", "MY_KEY", "OpenAI");
        when(repository.findByIdAndUserId(s.getId(), "user-1")).thenReturn(Optional.of(s));
        when(vaultClient.read(any())).thenThrow(new RuntimeException("Vault unreachable"));
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        TestResult result = service.testSecret("user-1", s.getId());
        assertThat(result).isEqualTo(TestResult.UNREACHABLE);
    }

    @Test
    void testSecret_notFound_returnsInvalid() {
        when(repository.findByIdAndUserId("missing", "user-1")).thenReturn(Optional.empty());
        TestResult result = service.testSecret("user-1", "missing");
        assertThat(result).isEqualTo(TestResult.INVALID);
        verify(vaultClient, never()).read(any());
    }

    @Test
    void resolveValueByName_readsFromVaultByName() {
        SecretMetadata s = SecretMetadata.create("user-1", "MY_OPENAI_KEY", "OpenAI");
        when(repository.findByUserIdAndName("user-1", "MY_OPENAI_KEY")).thenReturn(Optional.of(s));
        when(vaultClient.read(s.getVaultPath())).thenReturn(Map.of("value", "sk-real-value"));
        Optional<String> value = service.resolveValueByName("user-1", "MY_OPENAI_KEY");
        assertThat(value).contains("sk-real-value");
    }

    @Test
    void resolveValueByName_notFound_returnsEmpty() {
        when(repository.findByUserIdAndName("user-1", "MISSING_KEY")).thenReturn(Optional.empty());
        Optional<String> value = service.resolveValueByName("user-1", "MISSING_KEY");
        assertThat(value).isEmpty();
    }
}
