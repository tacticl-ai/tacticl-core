package io.tacticl.data.connections.entity;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.UUID;

@Document("secret_metadata")
public class SecretMetadata {

    @Id
    private String id;

    @Indexed
    private String userId;

    private String name;
    private String providerHint;
    private String vaultPath;
    private TestResult lastTestResult;
    private Instant lastTestedAt;
    private Instant createdAt;

    protected SecretMetadata() {}

    public static SecretMetadata create(String userId, String name, String providerHint) {
        SecretMetadata s = new SecretMetadata();
        s.id = UUID.randomUUID().toString();
        s.userId = userId;
        s.name = name;
        s.providerHint = providerHint;
        s.vaultPath = "tacticl/" + userId + "/secrets/" + s.id;
        s.lastTestResult = TestResult.UNTESTED;
        s.createdAt = Instant.now();
        return s;
    }

    public void markTested(TestResult result) {
        this.lastTestResult = result;
        this.lastTestedAt = Instant.now();
    }

    public String getId() { return id; }
    public String getUserId() { return userId; }
    public String getName() { return name; }
    public String getProviderHint() { return providerHint; }
    public String getVaultPath() { return vaultPath; }
    public TestResult getLastTestResult() { return lastTestResult; }
    public Instant getLastTestedAt() { return lastTestedAt; }
    public Instant getCreatedAt() { return createdAt; }
}
