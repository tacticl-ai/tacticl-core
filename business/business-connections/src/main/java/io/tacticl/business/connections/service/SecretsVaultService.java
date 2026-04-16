package io.tacticl.business.connections.service;

import io.cidadel.framework.secrets.client.VaultClient;
import io.tacticl.data.connections.entity.SecretMetadata;
import io.tacticl.data.connections.entity.TestResult;
import io.tacticl.data.connections.repository.SecretMetadataRepository;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class SecretsVaultService {

    private final SecretMetadataRepository repository;
    private final VaultClient vaultClient;

    public SecretsVaultService(SecretMetadataRepository repository, VaultClient vaultClient) {
        this.repository = repository;
        this.vaultClient = vaultClient;
    }

    public SecretMetadata store(String userId, String name, String providerHint, String value) {
        SecretMetadata metadata = SecretMetadata.create(userId, name, providerHint);
        vaultClient.write(metadata.getVaultPath(), Map.of("value", value));
        return repository.save(metadata);
    }

    public List<SecretMetadata> listSecrets(String userId) {
        return repository.findByUserId(userId);
    }

    public void delete(String userId, String secretId) {
        repository.findByIdAndUserId(secretId, userId).ifPresent(secret -> {
            vaultClient.delete(secret.getVaultPath());
            repository.deleteByIdAndUserId(secretId, userId);
        });
    }

    public Optional<String> resolveValue(String userId, String secretId) {
        return repository.findByIdAndUserId(secretId, userId)
                .map(s -> readVaultValue(s.getVaultPath()));
    }

    public Optional<String> resolveValueByName(String userId, String secretName) {
        return repository.findByUserIdAndName(userId, secretName)
                .map(s -> readVaultValue(s.getVaultPath()));
    }

    public TestResult testSecret(String userId, String secretId) {
        return repository.findByIdAndUserId(secretId, userId)
                .map(s -> {
                    try {
                        Map<String, Object> data = vaultClient.read(s.getVaultPath());
                        TestResult result = (data != null && data.containsKey("value"))
                                ? TestResult.VALID : TestResult.INVALID;
                        s.markTested(result);
                        repository.save(s);
                        return result;
                    } catch (Exception e) {
                        s.markTested(TestResult.UNREACHABLE);
                        repository.save(s);
                        return TestResult.UNREACHABLE;
                    }
                })
                .orElse(TestResult.INVALID);
    }

    private String readVaultValue(String vaultPath) {
        Map<String, Object> data = vaultClient.read(vaultPath);
        return data != null ? (String) data.get("value") : null;
    }
}
