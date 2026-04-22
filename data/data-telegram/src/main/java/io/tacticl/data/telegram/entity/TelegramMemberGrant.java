package io.tacticl.data.telegram.entity;

import io.tacticl.data.connections.base.BaseMongoEntity;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Objects;

@Document("telegram_member_grants")
@CompoundIndex(
    name = "project_user_unique",
    def = "{'projectId': 1, 'tacticlUserId': 1}",
    unique = true,
    partialFilter = "{ 'isActive': true }"
)
public class TelegramMemberGrant extends BaseMongoEntity {

    @Indexed
    private String projectId;

    @Indexed
    private long chatId;

    @Indexed
    private String tacticlUserId;

    private long telegramUserId;

    private MemberRole role;

    private String grantedByUserId;

    private Instant grantedAt;

    @Version
    private Long version;

    public static TelegramMemberGrant create(String projectId, long chatId, String tacticlUserId,
                                             long telegramUserId, MemberRole role, String grantedByUserId) {
        Objects.requireNonNull(projectId, "projectId");
        Objects.requireNonNull(tacticlUserId, "tacticlUserId");
        Objects.requireNonNull(role, "role");
        Objects.requireNonNull(grantedByUserId, "grantedByUserId");
        var g = new TelegramMemberGrant();
        g.projectId = projectId;
        g.chatId = chatId;
        g.tacticlUserId = tacticlUserId;
        g.telegramUserId = telegramUserId;
        g.role = role;
        g.grantedByUserId = grantedByUserId;
        g.grantedAt = Instant.now();
        return g;
    }

    public void updateRole(MemberRole newRole, String grantedByUserId) {
        Objects.requireNonNull(newRole, "newRole");
        Objects.requireNonNull(grantedByUserId, "grantedByUserId");
        this.role = newRole;
        this.grantedByUserId = grantedByUserId;
        this.grantedAt = Instant.now();
    }

    public String getProjectId() { return projectId; }
    public void setProjectId(String projectId) { this.projectId = projectId; }
    public long getChatId() { return chatId; }
    public void setChatId(long chatId) { this.chatId = chatId; }
    public String getTacticlUserId() { return tacticlUserId; }
    public void setTacticlUserId(String tacticlUserId) { this.tacticlUserId = tacticlUserId; }
    public long getTelegramUserId() { return telegramUserId; }
    public void setTelegramUserId(long telegramUserId) { this.telegramUserId = telegramUserId; }
    public MemberRole getRole() { return role; }
    public String getGrantedByUserId() { return grantedByUserId; }
    public Instant getGrantedAt() { return grantedAt; }
    public Long getVersion() { return version; }
}
